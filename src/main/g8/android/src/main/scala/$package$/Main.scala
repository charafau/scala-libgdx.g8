package $package$

import android.os.Bundle
import com.badlogic.gdx.backends.android._

class Main extends AndroidApplication {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val config = new AndroidApplicationConfiguration()
    initialize(new MainGame, config)
  }
}
