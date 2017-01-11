/**
 * Copyright 2017 Kartik Arora
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kartikarora.transfersh.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Developer: chipset
 * Package : me.kartikarora.transfersh.services
 * Project : Transfer.sh
 * Date : 11/1/17
 */

public class CheckAndNotifyOrDeleteService extends Service {
    public CheckAndNotifyOrDeleteService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
