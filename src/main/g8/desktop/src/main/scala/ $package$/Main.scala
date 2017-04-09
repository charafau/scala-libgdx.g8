package $package$

import com.badlogic.gdx.backends.lwjgl.{LwjglApplication, LwjglApplicationConfiguration}

object Main extends App {
    val config = new LwjglApplicationConfiguration
    new LwjglApplication(new MainGame, config)
}
