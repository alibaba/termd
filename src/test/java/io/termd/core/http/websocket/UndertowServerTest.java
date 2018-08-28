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

package io.termd.core.http.websocket;

import io.termd.core.function.Consumer;
import io.termd.core.function.Supplier;
import io.termd.core.http.websocket.client.Client;
import io.termd.core.http.websocket.server.TaskStatusUpdateEvent;
import io.termd.core.http.websocket.server.TermServer;
import io.termd.core.pty.Status;
import io.termd.core.util.MockProcess;
import io.termd.core.util.Wait;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class UndertowServerTest {

  private static final String TEST_COMMAND = "java -cp ./target/test-classes/ " + MockProcess.class.getName() + " 1 500";

  TermServer termServer;

  @Before
  public void startTermServer() throws InterruptedException {
    termServer = TermServer.start();
  }

  @Test
  public void serverShouldStart() throws Exception {
    URL url = new URL("http://" + Configurations.HOST + ":" + termServer.getPort() + "/");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setDoOutput(true);

    Assert.assertEquals(200, connection.getResponseCode());
  }

  @Test
  public void remoteCommandShouldBeExecutedAndResponsesReceived() throws Exception {
    doRemoteCommandShouldBeExecutedAndResponsesReceived("");
  }

  @Test
  public void remoteCommandShouldBeExecutedAndResponsesReceivedWithContext() throws Exception {
    doRemoteCommandShouldBeExecutedAndResponsesReceived("/the_context");
  }

  @Test
  public void clientShouldBeAbleToReConnectToARunningSession() throws Exception {
    String context = "/my-context";
    String processSocketUrl = "http://" + Configurations.HOST + ":" + termServer.getPort() +  Configurations.TERM_PATH + context;
    final StringBuilder responseData = new StringBuilder();
    Consumer<String> responseConsumer = new Consumer<String>() {
      @Override
      public void accept(String data) {
        responseData.append(data);
      }
    };

    Client client = Client.connectCommandExecutingClient(processSocketUrl, responseConsumer);
    Client.executeRemoteCommand(client, TEST_COMMAND);

    Wait.forCondition(new Supplier<Boolean>() {
      @Override
      public Boolean get() {
        return responseData.toString().contains(MockProcess.WELCOME_MESSAGE);
      }
    }, 60, TimeUnit.SECONDS);

    client.close();

    final StringBuilder reConnectingResponseData = new StringBuilder();
    Consumer<String> reConnectingResponseConsumer = new Consumer<String>() {
      @Override
      public void accept(String data) {
        reConnectingResponseData.append(data);
      }
    };

    Client reConnectingClient = Client.connectCommandExecutingClient(processSocketUrl, reConnectingResponseConsumer);
    Wait.forCondition(new Supplier<Boolean>() {
      @Override
      public Boolean get() {
        return reConnectingResponseData.toString().contains(MockProcess.FINAL_MESSAGE);
      }
    }, 60, TimeUnit.SECONDS);
    Assert.assertTrue("Missing response data.", reConnectingResponseData.toString().contains(MockProcess.FINAL_MESSAGE));
    Assert.assertTrue("Missing response data.", reConnectingResponseData.toString().contains(MockProcess.FINAL_MESSAGE));
  }

  @Test
  public void clientShouldBeAbleToConnectToARunningSession() throws Exception {
    String context = "/my-multi-client-context";
    String processSocketUrl = "http://" + Configurations.HOST + ":" + termServer.getPort() +  Configurations.TERM_PATH + context;
    final StringBuilder responseData = new StringBuilder();
    Consumer<String> responseConsumer = new Consumer<String>() {
      @Override
      public void accept(String data) {
        responseData.append(data);
      }
    };

    Client client = Client.connectCommandExecutingClient(processSocketUrl, responseConsumer);
    Client.executeRemoteCommand(client, TEST_COMMAND);

    Wait.forCondition(new Supplier<Boolean>() {
      @Override
      public Boolean get() {
        return responseData.toString().contains(MockProcess.WELCOME_MESSAGE);
      }
    }, 60, TimeUnit.SECONDS);

    final StringBuilder reConnectingResponseData = new StringBuilder();
    Consumer<String> reConnectingResponseConsumer = new Consumer<String>() {
      @Override
      public void accept(String data) {
        reConnectingResponseData.append(data);
      }
    };

    Client reConnectingClient = Client.connectCommandExecutingClient(processSocketUrl, reConnectingResponseConsumer);

    Wait.forCondition(new Supplier<Boolean>() {
      @Override
      public Boolean get() {
          return reConnectingResponseData.toString().contains(MockProcess.FINAL_MESSAGE);
      }
    }, 60, TimeUnit.SECONDS);
    Assert.assertTrue("Missing response data.", reConnectingResponseData.toString().contains(MockProcess.FINAL_MESSAGE));
    Assert.assertTrue("Missing response data.", reConnectingResponseData.toString().contains(MockProcess.FINAL_MESSAGE));
  }

  private void doRemoteCommandShouldBeExecutedAndResponsesReceived(String context) throws Exception {
    String updatesSocketUrl = "http://" + Configurations.HOST + ":" + termServer.getPort() + Configurations.PROCESS_UPDATES_PATH + context;
    final List<Status> receivedEventUpdates = new ArrayList<Status>();
    Consumer<TaskStatusUpdateEvent> onStatusUpdate = new Consumer<TaskStatusUpdateEvent>() {
      @Override
      public void accept(TaskStatusUpdateEvent statusUpdateEvent) {
        receivedEventUpdates.add(statusUpdateEvent.getNewStatus());
      }
    };

    Client.connectStatusListenerClient(updatesSocketUrl, onStatusUpdate);

    String processSocketUrl = "http://" + Configurations.HOST + ":" + termServer.getPort() +  Configurations.TERM_PATH + context;
    final StringBuilder responseData = new StringBuilder();
    Consumer<String> responseConsumer = new Consumer<String>() {
      @Override
      public void accept(String data) {
        responseData.append(data);
      }
    };

    Client client = Client.connectCommandExecutingClient(processSocketUrl, responseConsumer);

    Client.executeRemoteCommand(client, TEST_COMMAND);

    Wait.forCondition(new Supplier<Boolean>() {
      @Override
      public Boolean get() {
        return receivedEventUpdates.contains(Status.COMPLETED);
      }
    }, 60, TimeUnit.SECONDS);

    Assert.assertTrue("Missing response data.", responseData.toString().contains(MockProcess.FINAL_MESSAGE));
  }

}
