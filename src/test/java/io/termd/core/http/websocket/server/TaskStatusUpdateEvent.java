package io.termd.core.http.websocket.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;
import io.termd.core.pty.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TaskStatusUpdateEvent implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(TaskStatusUpdateEvent.class);

    private final String taskId;
    private final Status oldStatus;
    private final Status newStatus;
    private final String context;

    @JSONCreator
    public TaskStatusUpdateEvent(
            @JSONField(name = "taskId") String taskId,
            @JSONField(name = "oldStatus") Status oldStatus,
            @JSONField(name = "newStatus") Status newStatus,
            @JSONField(name = "context") String context) {
        this.taskId = taskId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.context = context;
    }

    public String getTaskId() {
        return taskId;
    }

    public Status getOldStatus() {
        return oldStatus;
    }

    public Status getNewStatus() {
        return newStatus;
    }

    public String getContext() {
        return context;
    }

    @Override
    public String toString() {
        try {
            return JSON.toJSONString(this);
        } catch (Exception e) {
            log.error("Cannot serialize object.", e);
        }
        return null;
    }

    public static TaskStatusUpdateEvent fromJson(String serialized) {
        try {
            return JSON.parseObject(serialized, TaskStatusUpdateEvent.class);
        } catch (Exception e) {
            log.error("Cannot deserialize object from json", e);
            return null;
        }
    }
}
