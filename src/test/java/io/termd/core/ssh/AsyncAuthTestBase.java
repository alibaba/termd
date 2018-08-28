package io.termd.core.ssh;

import com.jcraft.jsch.JSchException;
import io.termd.core.ssh.netty.AsyncAuth;
import io.termd.core.ssh.netty.AsyncUserAuthServiceFactory;
import io.termd.core.TestBase;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerConnectionServiceFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.util.test.EchoShellFactory;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class AsyncAuthTestBase extends TestBase {

  SshServer server;

  private PasswordAuthenticator authenticator;

  public void startServer() throws Exception {
    startServer(null);
  }

  public void startServer(Integer timeout) throws Exception {
    if (server != null) {
      throw failure("Server already started");
    }
    server = SshServer.setUpDefaultServer();
    if (timeout != null) {
      server.getProperties().put(FactoryManager.AUTH_TIMEOUT, timeout.toString());
    }
    server.setPort(5000);
    server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("hostkey.ser").toPath()));
    server.setPasswordAuthenticator(new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
        return authenticator.authenticate(username, password, session);
      }
    });
    server.setShellFactory(new EchoShellFactory());
    server.setServiceFactories(Arrays.asList(ServerConnectionServiceFactory.INSTANCE, AsyncUserAuthServiceFactory.INSTANCE));
    server.start();
  }

  @After
  public void stopServer() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void testSyncAuthFailed() throws Exception {
    startServer();
    authenticator = new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
        return false;
      }
    };
    assertFalse(authenticate());
  }

  @Test
  public void testSyncAuthSucceeded() throws Exception {
    startServer();
    authenticator = new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
        return true;
      }
    };
    assertTrue(authenticate());
  }

  @Test
  public void testAsyncAuthFailed() throws Exception {
    startServer();
    authenticator = new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
        final AsyncAuth auth = new AsyncAuth();
        new Thread() {
          @Override
          public void run() {
            try {
              Thread.sleep(200);
            } catch (InterruptedException ignore) {
            } finally {
              auth.setAuthed(false);
            }
          }
        }.start();
        throw auth;
      }
    };
    assertFalse(authenticate());
  }

  @Test
  public void testAsyncAuthSucceeded() throws Exception {
    startServer();
    authenticator = new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
        final AsyncAuth auth = new AsyncAuth();
        new Thread() {
          @Override
          public void run() {
            try {
              Thread.sleep(200);
            } catch (InterruptedException ignore) {
            } finally {
              auth.setAuthed(true);
            }
          }
        }.start();
        throw auth;
      }
    };
    assertTrue(authenticate());
  }

  @Test
  public void testAsyncAuthTimeout() throws Exception {
    startServer(500);
    authenticator = new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
        throw new AsyncAuth();
      }
    };
    try {
      authenticate();
    } catch (JSchException e) {
      assertTrue("Unexpected failure " + e.getMessage(), e.getMessage().startsWith("SSH_MSG_DISCONNECT"));
    }
  }

  @Test
  public void testAsyncAuthSucceededAfterTimeout() throws Exception {
    startServer(500);
    authenticator = new PasswordAuthenticator() {
      @Override
      public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
        final AsyncAuth auth = new AsyncAuth();
        new Thread() {
          @Override
          public void run() {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            } finally {
              auth.setAuthed(true);
            }
          }
        }.start();
        throw auth;
      }
    };
    try {
      authenticate();
    } catch (JSchException e) {
      assertTrue("Unexpected failure " + e.getMessage(), e.getMessage().startsWith("SSH_MSG_DISCONNECT"));
    }
  }

  protected abstract boolean authenticate() throws Exception;

}
