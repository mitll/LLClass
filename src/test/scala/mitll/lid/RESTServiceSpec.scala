/*
 * Copyright 2013-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 * version 0.2
 * author: Wade Shen, Jennifer Williams, Gordon Vidaver
 * dagli@ll.mit.edu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mitll.lid


import com.typesafe.scalalogging.LazyLogging
import org.scalatest._

class RESTServiceSpec extends FlatSpec with Matchers with LazyLogging {
  it should "start a service" in {
    val service = new RESTService
    try {
      Thread.sleep(60000)
    } catch {
      case e:Any => println("got error " + e)
    }
  }
}
