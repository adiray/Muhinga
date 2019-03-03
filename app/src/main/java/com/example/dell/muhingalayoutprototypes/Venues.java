package com.example.dell.muhingalayoutprototypes;

import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.mikepenz.fastadapter.adapters.FooterAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter_extensions.items.ProgressItem;
import com.mikepenz.fastadapter_extensions.scroll.EndlessRecyclerOnScrollListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class Venues extends AppCompatActivity {

    //miscellaneous objects
    Boolean onRefreshing= false, infiniteLoading=false;
    Boolean onFilteredRefreshing = false, filteredInfiniteLoading = false; //shows whether the user is refeshing or loading more filtered items;
    ArrayList<VenuesResponse> allVenuesResponseArray = new ArrayList<>(), filteredVenuesResponseArray = new ArrayList<>();
    ArrayList<String> queryStringsHolder = new ArrayList<>();
    ArrayList<String> locationOptions = new ArrayList<String>();
    String queryString = null;
    StringBuilder mb = new StringBuilder();
    Boolean isForRent = false;
    Boolean isForSale = false, filteredState = false; //filtered state helps determine whether the user has applied filters to the items to be returned

    //declare view objects
    Button filterVenuesButton;
    EditText venuesPriceEditText;
    Spinner venuesLocationSpinner;


    //recycler view objects
    RecyclerView venuesMainRecView;
    SwipeRefreshLayout venuesMainRecViewSwipeRefresh;

    //declare the retrofit objects. All these are used with retrofit
    Retrofit.Builder builder;
    Retrofit myRetrofit;
    RetrofitClient myWebClient;
    retrofit2.Call<ArrayList<VenuesResponse>> allVenuesCall, filteredVenuesCall, locationsCall;

    //holds the dynamic parameters used in the request url query
    Map<String, String> venuesFilterMap = new HashMap<String, String>();
    Map<String, String> venuesUserFilterMap = new HashMap<>();
    Map<String, String> locationsMap = new HashMap<>(); //holds the query for the locations

    Integer tableOffset = 0, filteredTableOffset = 0;   //this increases the offset from the top of the table when items are being retrieved from backendless
    String tableOffsetString = tableOffset.toString(), filteredTableOffsetString = filteredTableOffset.toString();


    //fast adapter objects
    FastItemAdapter<VenuesResponse> venuesRecViewFastAdapter;
    EndlessRecyclerOnScrollListener endlessRecyclerOnScrollListener;
    FooterAdapter<ProgressItem> footerAdapter = new FooterAdapter<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_venues);


        //initialize the views
        venuesMainRecViewSwipeRefresh = findViewById(R.id.venues_swipe_refresh);
        filterVenuesButton = findViewById(R.id.submit_venues_filter_button);
        venuesLocationSpinner = findViewById(R.id.venues_location_spinner);
        venuesPriceEditText = findViewById(R.id.venues_max_price_edit_text);


        //build out main recycler view
        venuesMainRecView = findViewById(R.id.venues_activity_rec_view);
        venuesMainRecView.setHasFixedSize(true);
        venuesMainRecView.setLayoutManager(new GridLayoutManager(Venues.this, 1, 1, false));

        //initialize the fast adapter which will manage everything
        venuesRecViewFastAdapter = new FastItemAdapter<>();

        //initialize the endless scroll listener
        endlessRecyclerOnScrollListener = new EndlessRecyclerOnScrollListener(footerAdapter) {
            @Override
            public void onLoadMore(int currentPage) {


                footerAdapter.clear();
                footerAdapter.add(new ProgressItem().withEnabled(false));

                //a statement to check if the user is loading more items that have been filtered or just loading more of all items unfiltered
                if (filteredState) {
                    loadMoreFilteredVenues();
                } else {
                    loadMoreVenues();
                }

            }
        };


        venuesMainRecViewSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                //a statement to check if the user is refreshing items that have been filtered or just refreshing all items unfiltered
                if (filteredState) {
                    refreshFilteredVenues();
                } else {
                    refreshVenues();
                }
                Log.d("myLogsrefreshingvalue", onRefreshing.toString());


            }
        });


        venuesMainRecView.addOnScrollListener(endlessRecyclerOnScrollListener);


        //fill the query map object for the retrofit query
        venuesFilterMap.put("pageSize", "4");
        venuesFilterMap.put("offset", tableOffsetString);
        venuesFilterMap.put("sortBy", "created%20desc");


        buildRetrofitClient();  //build the retrofit client

        requestVenues();    //make the initial / first  houses request

        populateLocations(); //add the locations to the locations spinner


        filterVenuesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                getFilters();
                requestFilteredVenues();
                filteredState = true;

            }
        });


    }


    /*************************************************************************************************************************************************/


    void buildRetrofitClient() {

        //initialize the retrofit client builder using the backendless.com api
        builder = new Retrofit.Builder();
        builder.baseUrl("https://api.backendless.com/54158ACA-8614-7763-FF72-3BFBF61B4600/167C910C-C110-5D40-FF09-C8621E23B700/data/")
                .addConverterFactory(GsonConverterFactory.create());

        //use your builder to build a retrofit object
        myRetrofit = builder.build();

        //create a retrofit client using the retrofit object
        myWebClient = myRetrofit.create(RetrofitClient.class);

        //create your call using the retrofit client
        allVenuesCall = myWebClient.getFilteredVenues(venuesFilterMap);
        filteredVenuesCall = myWebClient.getFilteredVenues(venuesUserFilterMap);
        locationsCall = myWebClient.getFilteredVenues(locationsMap);

    }


    /*************************************************************************************************************************************************/


    void requestVenues() {

        //make the call
        allVenuesCall.clone().enqueue(new Callback<ArrayList<VenuesResponse>>() {
            @Override
            public void onResponse(Call<ArrayList<VenuesResponse>> call, Response<ArrayList<VenuesResponse>> response) {

                if (!onRefreshing && !infiniteLoading) {

                    //perform the normal sequence of actions for a first time load
                    allVenuesResponseArray = response.body();
                    venuesRecViewFastAdapter.add(allVenuesResponseArray);
                    venuesMainRecView.setAdapter(footerAdapter.wrap(venuesRecViewFastAdapter));


                    Log.d("myLogsRequestUrl", response.raw().request().url().toString());

                } else if (onRefreshing && !infiniteLoading) {

                    //perform the sequence of actions for a refreshed load
                    allVenuesResponseArray.clear();
                    allVenuesResponseArray = response.body();
                    venuesRecViewFastAdapter.clear();
                    venuesMainRecView.clearOnScrollListeners();
                    venuesMainRecView.addOnScrollListener(endlessRecyclerOnScrollListener);
                    venuesRecViewFastAdapter.add(response.body());
                    endlessRecyclerOnScrollListener.resetPageCount();


                    Log.d("myLogsRequestUrlOR", response.raw().request().url().toString());


                } else if (infiniteLoading && !onRefreshing) {

                    allVenuesResponseArray.addAll(response.body());
                    footerAdapter.clear();
                    if (response.body().size() > 0) {
                        venuesRecViewFastAdapter.add(response.body());
                    } else {
                        Toast.makeText(Venues.this, "No more items", Toast.LENGTH_LONG).show();
                    }


                    Log.d("myLogsRequestUrlIL", response.raw().request().url().toString() + " table offset = " + tableOffset);
                    infiniteLoading = false;


                }

                Log.d("myLogsOnSuccess", "onResponse: response successful");


            }

            @Override
            public void onFailure(Call<ArrayList<VenuesResponse>> call, Throwable t) {

                Log.d("myLogsOnFailure", "onResponse: response unsuccessful");

            }
        });

    }

    /*************************************************************************************************************************************************/


    /*************************************************************************************************************************************************/


    void refreshVenues() {    //method called when user attempts to refresh the houses recycler view


        tableOffset = 0;
        tableOffsetString = tableOffset.toString();
        venuesFilterMap.put("offset", tableOffsetString);  //update the value of the offset in the request url
        onRefreshing = true;
        infiniteLoading = false;
        requestVenues();

        //stop the refreshing animation
        venuesMainRecViewSwipeRefresh.setRefreshing(false);
    }


    /*************************************************************************************************************************************************/


    void loadMoreVenues() {

        //TODO The allVenuesResponseArray exists just to give a count. Maybe the count could be more effectively stored in an integer value?
        tableOffset = allVenuesResponseArray.size();
        tableOffsetString = tableOffset.toString();
        venuesFilterMap.put("offset", tableOffsetString);    //update the value of the offset in the request url
        Log.d("myLogs", "loadMoreHouses: " + venuesFilterMap.toString());
        infiniteLoading = true;
        onRefreshing = false;
        requestVenues();
    }


    /*************************************************************************************************************************************************/


    void getFilters() {

        //clear the query filter and the string builder mb
        queryString = null;
        mb.delete(0, mb.length());
        queryStringsHolder.clear();

        //the if statement checks if the venues location spinner's selected item is 'all' and skips adding the query if it is
        if (!venuesLocationSpinner.getSelectedItem().equals("ALL")) {

            //this adds "AND" to the query if it is appended to another query and adds nothing if it is alone
            if (queryStringsHolder.isEmpty()) {
                queryStringsHolder.add("location%3D" + "'" + venuesLocationSpinner.getSelectedItem().toString() + "'");
            } else {
                queryStringsHolder.add("%20AND%20Location%3D" + "'" + venuesLocationSpinner.getSelectedItem().toString() + "'");
            }
        }


        //this adds the data from the Price edit text
        if (!venuesPriceEditText.getText().toString().isEmpty()) {

            if (queryStringsHolder.isEmpty()) {
                queryStringsHolder.add("price%3C%3D" + venuesPriceEditText.getText().toString());
            } else {
                queryStringsHolder.add("%20AND%20price%3C%3D" + venuesPriceEditText.getText().toString());
            }

        }


        for (int i = 0; i < queryStringsHolder.size(); i++) {

            mb.append(queryStringsHolder.get(i));

        }

        queryString = mb.toString();
        Log.d(queryString, "mquery");


        venuesUserFilterMap.putAll(venuesFilterMap);

        //update the value of the offset in the request url before you make the call
        filteredTableOffset = 0;
        filteredTableOffsetString = filteredTableOffset.toString();
        venuesUserFilterMap.put("offset", filteredTableOffsetString);

        venuesUserFilterMap.put("where", queryString);

        //ToDo What if the user refreshes a set of filtered calls?


    }


    /*************************************************************************************************************************************************/


    void populateLocations() {

        //add an All locations option for the user to select all available locations
        locationOptions.add(getString(R.string.ALL));

        locationsMap.put("props", "Location");

        //initialize the spinner and assign it an adapter
        ArrayAdapter<String> locationSpinnerAdapter = new ArrayAdapter<>(Venues.this, android.R.layout.simple_spinner_item, locationOptions);
        //set the layout resource for the spinner adapter
        locationSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        venuesLocationSpinner.setAdapter(locationSpinnerAdapter);

        locationsCall.clone().enqueue(new Callback<ArrayList<VenuesResponse>>() {
            @Override
            public void onResponse(Call<ArrayList<VenuesResponse>> call, Response<ArrayList<VenuesResponse>> response) {

                String stringHolder;
                int rSize = response.body().size();

                //a for statement to cycle through the response and add every unique location to the locationOptions array
                for (int counter = 0; counter < rSize; counter++) {
                    stringHolder = response.body().get(counter).getLocation();
                    if (!locationOptions.contains(stringHolder)) {
                        locationOptions.add(stringHolder);
                    }
                }


            }

            @Override
            public void onFailure(Call<ArrayList<VenuesResponse>> call, Throwable t) {

                Toast.makeText(Venues.this, "No locations Available", Toast.LENGTH_LONG).show();

            }
        });


    }


    /*************************************************************************************************************************************************/

    void requestFilteredVenues() {

        filteredVenuesCall.clone().enqueue(new Callback<ArrayList<VenuesResponse>>() {
            @Override
            public void onResponse(Call<ArrayList<VenuesResponse>> call, Response<ArrayList<VenuesResponse>> response) {

                //do not use the diffutil because we are loading  only ten items at a time and there is no justification for it to filter only ten items.

                if (response.isSuccessful()) {
                    if (!filteredInfiniteLoading && !onFilteredRefreshing) {
                        filteredVenuesResponseArray = response.body();
                        venuesRecViewFastAdapter.clear();
                        venuesMainRecView.clearOnScrollListeners();
                        venuesMainRecView.addOnScrollListener(endlessRecyclerOnScrollListener);
                        venuesRecViewFastAdapter.add(response.body());
                        endlessRecyclerOnScrollListener.resetPageCount();

                    } else if (filteredInfiniteLoading && !onFilteredRefreshing) {

                        filteredVenuesResponseArray.addAll(response.body());
                        footerAdapter.clear();
                        if (response.body().size() > 0) {
                            venuesRecViewFastAdapter.add(response.body());
                        } else {
                            Toast.makeText(Venues.this, "No more items", Toast.LENGTH_LONG).show();
                        }

                        Log.d("myLogsRequestUrlFIL", response.raw().request().url().toString() + " table offset = " + tableOffset);
                        filteredInfiniteLoading = false;


                    } else if (onFilteredRefreshing && !filteredInfiniteLoading) {

                        filteredVenuesResponseArray.clear();
                        filteredVenuesResponseArray = response.body();
                        venuesRecViewFastAdapter.clear();
                        venuesMainRecView.clearOnScrollListeners();
                        venuesMainRecView.addOnScrollListener(endlessRecyclerOnScrollListener);
                        venuesRecViewFastAdapter.add(response.body());
                        endlessRecyclerOnScrollListener.resetPageCount();


                        Log.d("myLogsRequestUrlFOR", response.raw().request().url().toString());
                    }
                } else {
                    Toast.makeText(Venues.this, "check the filters", Toast.LENGTH_LONG).show();
                }

            }

            @Override
            public void onFailure(Call<ArrayList<VenuesResponse>> call, Throwable t) {

                Log.d("myLogsFilteredFail", " Request has failed because " + t.getMessage());
            }
        });

    }


    /*************************************************************************************************************************************************/


    void loadMoreFilteredVenues() {

        filteredInfiniteLoading = true;
        onFilteredRefreshing = false;
        filteredTableOffset = filteredVenuesResponseArray.size();
        filteredTableOffsetString = filteredTableOffset.toString();
        venuesUserFilterMap.put("offset", filteredTableOffsetString);
        requestFilteredVenues();

    }


    /*************************************************************************************************************************************************/

    void refreshFilteredVenues() {


        filteredTableOffset = 0;
        filteredTableOffsetString = filteredTableOffset.toString();
        venuesUserFilterMap.put("offset", filteredTableOffsetString);  //update the value of the offset in the request url
        onFilteredRefreshing = true;
        filteredInfiniteLoading = false;
        requestFilteredVenues();

        //stop the refreshing animation
        venuesMainRecViewSwipeRefresh.setRefreshing(false);

    }


}
