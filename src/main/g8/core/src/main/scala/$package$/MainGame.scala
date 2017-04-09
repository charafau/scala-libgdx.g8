package $package$

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.{GL20, Texture}
import com.badlogic.gdx.{Game, Gdx}

class MainGame extends Game {

  lazy val sprite = new Texture("badlogic.jpg")
  lazy val batch = new SpriteBatch

  override def create(): Unit = {}

  override def render(): Unit = {
    Gdx.gl.glClearColor(1, 0, 0, 1)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    batch.begin()
    batch.draw(sprite, 0, 0)
    batch.end()
  }

  override def dispose(): Unit = {
    batch.dispose()
    sprite.dispose()
  }
}
