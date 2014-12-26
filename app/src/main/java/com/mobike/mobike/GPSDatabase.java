package com.mobike.mobike;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import static com.mobike.mobike.DatabaseStrings.*;
/**
 * This class has two important features: it has a reference to the helper object defined
 * in the inner class and contains the method that the other components of the app
 * will use to perform operations and queries on the database.
 *
 */

public class GPSDatabase
{
    private Context context;
    private DbHelper dbHelper; //the reference to the Helper object

    public final String DBNAME="gpsdb";
    public final int DBVERSION=1;

    // The raw code to initialize the database and the (only) table it will use.
    public final String CREATERDB="create table location(orderId integer primary key, " +
            "latitude varchar not null, longitude varchar not null, altitude varchar, " +
            "instant datetime default current_timestamp);";


    /**
     * The constructor, that creates the DBHelper object.
     * @param context
     */
    public GPSDatabase(Context context){
        this.context = context;
        dbHelper = new DbHelper(context);
    }


    /**
     * This class manages the creation and the upgrade of the database and gives a
     * reference to the helper object to retrieve data from the actual database.
     */
     public class DbHelper extends SQLiteOpenHelper {

        /**
         * This constructor method create the object that will make possible to perform operation
         * on the database DBNAME.
         * @param context
         */
        public DbHelper(Context context){
            super(context,DBNAME,null,DBVERSION);
        }

        /*
            Questo metodo viene invocato una volta sola, cioe' quando
            non esiste un db con nome DBNAME, ed esegue il codice "raw" contenuto in CREATERDB
         */

        /** This method is invoked only once, when it does not exists a database DBNAME.
         * It executes the raw SQLite code in CREATERDB.
         * @param db
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATERDB);
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // NOT TODO Auto-generated method stub
        }
    }

    /**
     * This method adds a new row to the database.
     * @param lat the latitude of the new location
     * @param lng the llongitude of the new locatio
     * @param alt the allatitude of the new locatio
     * @return
     */
    public long insertRow(double lat, double lng, double alt)
    {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues value=new ContentValues();
        value.put(FIELD_LAT, lat + "");
        value.put(FIELD_LNG, lng + "");
        value.put(FIELD_ALT, alt + "");
        db.insert(TABLENAME,null,value);
        try
        {
            return db.insert(DatabaseStrings.TABLENAME, null, value);
        }
        catch (SQLiteException sqle)
        {
            // not yet implemented
        }
        return 0;
    }

    /**
     * This method performs a query for all the rows in the table TABLENAME.
     * @return cursor, a Cursor object.
     */
    private Cursor getAllRows(){
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.query(TABLENAME,
                new String[]{FIELD_ID,FIELD_LAT,FIELD_LNG,FIELD_ALT, FIELD_TIME}, null,null, null, null, null);
    }

    /**
     * This method deletes all the rows from the table.
     */
    public void deleteTable(){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLENAME);
    }

    /**
     * This method creates a JSON that contains all the data in the database.
     * @return JSONArray
     */
    public JSONArray getTableInJSON(){
        Cursor cursor = getAllRows();
        cursor.moveToFirst();

        JSONArray resultSet = new JSONArray();
        JSONObject returnObj = new JSONObject();
        int totalColumn;

        while (!cursor.isAfterLast()){
            totalColumn = cursor.getColumnCount();

            JSONObject rowObject = new JSONObject();

            for( int i=0 ;  i< totalColumn ; i++ )
            {
                if( cursor.getColumnName(i) != null )
                {
                    try
                    {
                        if( cursor.getString(i) != null )
                        {
                            rowObject.put(cursor.getColumnName(i) ,  cursor.getString(i) );
                        }
                        else
                        {
                            rowObject.put( cursor.getColumnName(i) ,  "" );
                        }
                    }
                    catch( Exception e )
                    {
                        Log.d("TAG_NAME", e.getMessage());
                    }
                }

            }

            resultSet.put(rowObject);
            cursor.moveToNext();
        }

        cursor.close();
        Log.d("TAG_NAME", resultSet.toString() );
        return resultSet;
    }

    /**
     * This method performs a query on the columns of the latitude and the longitude
     * for all the rows.
     * @return an ArrayList<LatLng> containing all the recorded locations.
     */
    public ArrayList<LatLng> getAllLocations() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(TABLENAME,
                new String[]{FIELD_LAT, FIELD_LNG}, null, null, null, null, null);

        ArrayList<LatLng> returnLst = new ArrayList<LatLng>();

        cursor.moveToFirst();

        double latitude;
        double longitude;

        while (!cursor.isAfterLast()) {
            latitude = Double.parseDouble(cursor.getString(0));
            longitude = Double.parseDouble(cursor.getString(1));

            LatLng latLng = new LatLng(latitude, longitude);
            returnLst.add(latLng);

            cursor.moveToNext();
        }

        cursor.close();
        return returnLst;
    }


    /**
     * This method creates a reference to the database.
     * @throws SQLException
     */
    public void open() throws SQLException {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        //return true;
    }


    public void close(){
        dbHelper.close();
        //return true;
    }
}