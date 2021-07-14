package io.javalin.hotswap

import io.javalin.http.Context

object TestClass {
  fun home(ctx: Context){
    ctx.result("hello world")
  }
}