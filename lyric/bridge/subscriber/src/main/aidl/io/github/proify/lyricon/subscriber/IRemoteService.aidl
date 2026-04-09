// IRemoteService.aidl
package io.github.proify.lyricon.subscriber;

import  io.github.proify.lyricon.subscriber.IActivePlayerListener;
import android.os.SharedMemory;

interface IRemoteService {
    void setActivePlayerListener(in IActivePlayerListener listener);
    SharedMemory getActivePlayerPositionMemory();
    void disconnect();
}