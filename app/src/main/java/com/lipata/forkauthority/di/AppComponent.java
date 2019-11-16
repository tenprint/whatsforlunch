package com.lipata.forkauthority.di;

import com.lipata.forkauthority.poll.PollActivity;
import com.lipata.forkauthority.businesslist.BusinessListActivity;

import dagger.Component;

@ApplicationScope
@Component(modules = {AppModule.class, YelpModule.class, FirebaseModule.class})
public interface AppComponent {
    void inject(BusinessListActivity target);

    void inject(PollActivity activity);
}