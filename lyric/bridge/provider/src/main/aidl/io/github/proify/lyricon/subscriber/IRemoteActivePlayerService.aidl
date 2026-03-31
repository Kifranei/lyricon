package io.github.proify.lyricon.subscriber;

import io.github.proify.lyricon.subscriber.IActivePlayerListener;

interface IRemoteActivePlayerService {
    void addActivePlayerListener(IActivePlayerListener listener);
    void removeActivePlayerListener(IActivePlayerListener listener);
    void disconnect();
}
