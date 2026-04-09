// ISubscriberBinder.aidl
package io.github.proify.lyricon.subscriber;

import io.github.proify.lyricon.subscriber.IRemoteService;

interface ISubscriberBinder {
    void onRegistrationCallback(IRemoteService service);
    byte[] getSubscriberInfo();
}