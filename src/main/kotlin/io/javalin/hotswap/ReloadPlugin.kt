package io.javalin.hotswap

import org.hotswap.agent.annotation.Init
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.command.ReflectionCommand
import org.hotswap.agent.command.Scheduler
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
  description = "Testing reload plugin",
  testedVersions = ["unknown"]
)
class ReloadPlugin {

  //next up, each plugin needs a single static method which is called by hotswap agent when it is discovered so this
  //functionality needs to go into a companion object so we can use JvmStatic on it
  companion object {

    //Hotswap Agent has its own basic logger so it makes no assumptions about logging frameworks
    val logger: AgentLogger = AgentLogger.getLogger(ReloadPlugin::class.java)

    //here, we need to hook into something we know is going to be loaded.  I think this could probably be one of the
    //javalin core classes such as JavalinServer but for now I have just set it to a class I know exists.
    @JvmStatic
    @OnClassLoadEvent(classNameRegexp = "io.javalin.hotswap.App")
    fun onApplicationStart(ctClass: CtClass) {

      //add something to log so we know it happened
      logger.info("reload plugin initialised")

      //okay.  I have no idea why this is needed as the documentation on these plugins is terrible!  I raised an issue
      //as this method was being called but the plugin wasn't getting called on class changes and this is what they
      //told me I needed to do
      //
      //https://github.com/HotswapProjects/HotswapAgentExamples/issues/70
      var pluginCode = PluginManagerInvoker.buildInitializePlugin(ReloadPlugin::class.java)

      //this is really just for ease of calling a method on the app instance.  Since we are running in a different
      //classloader, the plugin doesn't know about application classes and it all has to be done via reflection. This
      //makes the App class call out to the plugin whenever it is initialised and pass a reference to App to the plugin
      pluginCode += PluginManagerInvoker.buildCallPluginMethod(
        ReloadPlugin::class.java,
        "setAppInstance",
        "this",
        "java.lang.Object"
      )

      //and now we add that generated code to the constructors so it gets called
      ctClass.declaredConstructors.asList().forEach {
        it.insertAfter(pluginCode)
      }
    }
  }

  //Hotswap Agent has some form of dependency injection.  In this case I want a scheduler to run commands
  @Init
  lateinit var scheduler: Scheduler
  lateinit var app: Any

  //this is called by the java code we injected into App so we know the relevant instance
  fun setAppInstance(app: Any){
    println("app instance set to $app")
    this.app = app
  }

  //finally, this is the meat of the whole thing.  After all the ceremony above, this method will now be called when
  //any class at all is changed.  We then use a reflection command to call the reload method
  @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
  fun onClassReloaded() {

    //log that we get called so we know it is actually working
    println("class reloaded so we need to restart the application")

    //try to call the reload method on the app via reflection
    scheduler.scheduleCommand(
      ReflectionCommand(app,"reload")
    )
  }
}