package io.javalin.hotswap

import org.hotswap.agent.annotation.*
import org.hotswap.agent.command.ReflectionCommand
import org.hotswap.agent.command.Scheduler
import org.hotswap.agent.config.PluginConfiguration
import org.hotswap.agent.javassist.CtClass
import org.hotswap.agent.logging.AgentLogger
import org.hotswap.agent.util.PluginManagerInvoker


//Okay, so this is the meat of the reloading concept.  We have to implement a HotswapAgent plugin.  There are lots of
//examples at
//
//https://github.com/HotswapProjects/HotswapAgent/tree/master/plugin
//
//but they are generally so complex as to be hard to follow.  I therefore mainly went off the example plugin here
//
//https://github.com/HotswapProjects/HotswapAgentExamples/tree/master/custom-plugin
//
//we start off by annotating the class with @Plugin and giving it the required parameters
@Plugin(
  name = "Reload Plugin",
  description = "Automatically reloads application on any class change",
  testedVersions = ["unknown"]
)
class ReloadPlugin {

  //first up, all Hotswap Agent Plugins need a single static method so it can be called on the load of a specific class
  //in order to hook into the loading lifecycle.  In kotlin terms a static method is in a companion object
  companion object {

    //Hotswap Agent has its own basic logger so it makes no assumptions about logging frameworks
    private val logger: AgentLogger = AgentLogger.getLogger(ReloadPlugin::class.java)

    //now, we need to make sure the plugin is initialised when the relevant classes are loaded.  In our case, we care
    //about Javalin being loaded so we hook into the class load event for io.javalin.Javalin
    @JvmStatic
    @OnClassLoadEvent(classNameRegexp = "io.javalin.Javalin")
    fun onApplicationStart(ctClass: CtClass) {
      //add something to log so we know it happened
      logger.info("reload plugin initialised")

      //At this point we need the plugin to be initialised when the class is created, so we need to modify the classes
      //constructors with the plugin initialisation code.  This was confusing hence the below query
      //
      //https://github.com/HotswapProjects/HotswapAgentExamples/issues/70
      val pluginCode = PluginManagerInvoker.buildInitializePlugin(ReloadPlugin::class.java)

      //and now we add that generated code to the constructors so it gets called
      ctClass.declaredConstructors.asList().forEach {
        it.insertAfter(pluginCode)
      }
    }
  }

  //HotswapAgent has a scheduler which merges together commands so that if you do a full rebuild, you don't end up
  //calling your reload method 8 million times.  It also allows this to be injected into your plugin on initialisation
  @Init
  lateinit var scheduler: Scheduler

  //HotswapAgent also has a config file which allows us to make this plugin generic by storing the main class in it
  @Init
  lateinit var pluginConfiguration: PluginConfiguration

  //and finally, since we need to get an instance of a class which is running in a different classloader we need to
  //get that classloader injected into our plugin so we can use it
  @Init
  lateinit var appClassLoader: ClassLoader

  //and last but not least we provide a lazy property so we don't call this a million times
  private val mainClassName by lazy { pluginConfiguration.getProperty("javalin.main_class") }

  //finally, this is the meat of the whole thing.  After all the ceremony above, this method will now be called when
  //any class at all is changed.  We then use a reflection command to call the restart method
  @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
  fun onClassReloaded() {
    logger.info("class reloaded - restarting application")
    restart()
  }

  //we also want to restart on change of any resource with the relevant pattern
  @OnResourceFileEvent(path="/", filter = ".*", events = [FileEvent.CREATE,FileEvent.DELETE,FileEvent.MODIFY])
  fun onResourceChanged(){
    logger.info("resource added or modified - restarting application")
    restart()
  }

  //function which performs the actual restart logic
  private fun restart(){
    //get an instance of the given class within the relevant classloader
    val cls = Class.forName(mainClassName,true,appClassLoader)

    //now, since it is a kotlin object we actually need the singleton INSTANCE property
    val instance = cls.getField("INSTANCE").get(cls)

    //and now we simply call restart
    scheduler.scheduleCommand(ReflectionCommand(instance,"restart"))
  }
}