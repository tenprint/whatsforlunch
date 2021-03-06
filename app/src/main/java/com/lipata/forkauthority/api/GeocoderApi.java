package com.lipata.forkauthority.api;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import com.lipata.forkauthority.di.ApplicationScope;

import java.io.IOException;

import javax.inject.Inject;

import io.reactivex.Single;
import timber.log.Timber;

@ApplicationScope
public class GeocoderApi {

    private final Geocoder geocoder;

    @Inject
    public GeocoderApi(final Context context) {
        geocoder = new Geocoder(context);
    }

    public Single<Address> getAddressObservable(final Location location) {
        return Single.fromCallable(() -> fetchAddress(location));
    }


    private Address fetchAddress(final Location location) throws IOException {
        Timber.d("fetchAddress()");
        return geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1).get(0);
    }

}
