package com.mobike.mobike;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mobike.mobike.network.POICreationTask;


public class POICreationActivity extends ActionBarActivity implements View.OnClickListener {

    private double latitude, longitude;
    private boolean recording;

    private Spinner categorySpinner;
    private int category;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poicreation);

        getSupportActionBar().hide();
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        ((Button) findViewById(R.id.create_button)).setOnClickListener(this);

        Intent intent = getIntent();
        latitude = intent.getDoubleExtra(MapsFragment.POI_LATITUDE, 300);
        longitude = intent.getDoubleExtra(MapsFragment.POI_LONGITUDE, 300);
        recording = intent.getBooleanExtra(MapsFragment.POI_RECORDING, false);

        categorySpinner = (Spinner) findViewById(R.id.poi_category);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.poi_categories, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        categorySpinner.setAdapter(adapter);
        categorySpinner.setPrompt("Route type...");
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                category = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //do nothing
            }
        });
        category = 0;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_poicreation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.create_button:
                createPOI();
                break;
        }
    }

    private void createPOI() {
        if (recording)
            savePOI();
        else
            sendPOI();
    }

    private void savePOI() {
        String title = ((EditText) findViewById(R.id.title)).getText().toString();
        // save POI in DB
        finish();
    }

    private void sendPOI() {
        String title = ((EditText) findViewById(R.id.title)).getText().toString();
        if (title.length() == 0) {
            Toast.makeText(this, "Please insert a name", Toast.LENGTH_SHORT).show();
            return;
        }
        new POICreationTask(this, latitude, longitude, title, category).execute();
        finish();
    }
}