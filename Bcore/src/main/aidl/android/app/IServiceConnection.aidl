/* //device/java/android/android/app/IServiceConnection.aidl
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package android.app;

import android.content.ComponentName;
import android.app.IBinderSession;

/** @hide */
oneway interface IServiceConnection {
    /**
     * @deprecated Use {@link #connected(ComponentName, IBinder, IBinderSession, boolean)} instead.
     */
    void connected(in ComponentName name, IBinder service);

    /**
     * Called when a service has been connected.
     *
     * @param name The concrete component name of the service that has been connected.
     * @param service The IBinder of the Service's communication channel.
     * @param session The IBinderSession for this connection.
     * @param dead Set to true if the service has been dead and the system is
     * delivering a replacement.
     */
    void connected(in ComponentName name, IBinder service, 
                   in IBinderSession session, boolean dead);
}
