package io.github.proify.lyricon.subscriber;

import io.github.proify.lyricon.subscriber.IRemoteActivePlayerService;

interface IActivePlayerBinder {
    void onRegistrationCallback(IRemoteActivePlayerService service);
}
