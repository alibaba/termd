/*
 * Copyright 2015 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.termd.core.term;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Map;

/**
 * A term info database.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 *
 * 修改人：柳志崇
 * 修改时间：2020-05-27
 * 修改备注：优先加载classpath://terminfo.src文件，找不到则加载默认的classpath://io/termd/core/term/terminfo.src文件。
 *          通过这种方式可以结合项目中的实际需要定制加载terminfo.src文件，以达到节约内存的目的。
 *          (加载默认的classpath://io/termd/core/term/terminfo.src文件到JVM约占用11M内存)
 *
 *
 */
public class TermInfo {

  private static TermInfo loadDefault() {
    try {
      //the first find path is classpath://terminfo.src
      InputStream in = TermInfo.class.getClassLoader().getResourceAsStream("terminfo.src");
      if(null==in){
        //the second find path is classpath://io/termd/core/term/terminfo.src
        in = TermInfo.class.getResourceAsStream("terminfo.src");
      }

      TermInfoParser parser = new TermInfoParser(new InputStreamReader(in, "US-ASCII"));
      TermInfoBuilder builder = new TermInfoBuilder();
      parser.parseDatabase(builder);
      return builder.build();
    } catch (Throwable t) {
      t.printStackTrace();
      return null;
    }
  }

  private static final TermInfo DEFAULT = loadDefault();

  /**
   * @return the default term info database loaded from the {@code terminfo.src} resource
   */
  public static TermInfo defaultInfo() {
    return DEFAULT;
  }

  final Map<String, Device> devices;

  TermInfo(Map<String, Device> devices) {
    this.devices = devices;
  }

  /**
   * Return a particular known device given its name.
   *
   * @param name the device name
   * @return the device or null
   */
  public Device getDevice(String name) {
    return devices.get(name);
  }

  /**
   * @return the devices present in this term info database.
   */
  public Collection<Device> devices() {
    return devices.values();
  }
}
