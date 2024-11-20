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

package io.termd.core.http.websocket.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.termd.core.pty.Status;

import java.io.IOException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
class TaskStatusUpdateEventDeserializer {

    public TaskStatusUpdateEvent deserialize(String jsonStr) throws IOException {
        JSONObject node = JSON.parseObject(jsonStr);

        String taskId = node.getString("taskId");
        String oldStatus = node.getString("oldStatus");
        String newStatus = node.getString("newStatus");
        String context = node.getString("context");

        return new TaskStatusUpdateEvent(taskId, Status.valueOf(oldStatus), Status.valueOf(newStatus), context);
    }
}

