package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by zhenggangxue on 4/9/17.
 */

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class OnTestClickListenerGDump implements View.OnClickListener {

    private static final String TAG = OnTestClickListenerLDump.class.getName();
    //    private static final int TEST_CNT = 50;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    private final TextView mTextView;
    private final ContentResolver mContentResolver;
    private final Uri mUri;

    public OnTestClickListenerGDump(TextView _tv, ContentResolver _cr) {
        mTextView = _tv;
        mContentResolver = _cr;
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public void onClick(View v) {
        new Task().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class Task extends AsyncTask<Void, String, Void> {

        @Override
        protected Void doInBackground(Void... params) {

//            testInsert();
//            publishProgress("Insert xueKey and value.\n");

            boolean gonnaQuery = true;
//            try{
//                Thread.sleep(100);
//            }catch (Exception e){
//                Log.e(TAG, e.getMessage());
//            }

            if(gonnaQuery){
                publishProgress("Begin to query!\n");
                Cursor myCursor = testQuery();
                myCursor.moveToFirst();
                while (!myCursor.isAfterLast()){
                    publishProgress(myCursor.getString(0)+" "+myCursor.getString(1)+"\n");
                    myCursor.moveToNext();
                }
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {
            mTextView.append(strings[0]);

            return;
        }

        private void testInsert(){
            ContentValues t = new ContentValues();
            t.put(KEY_FIELD, "xueKey1");
            t.put(VALUE_FIELD, "xueValue1");

            mContentResolver.insert(mUri,t);

            t = new ContentValues();
            t.put(KEY_FIELD, "xueKey2");
            t.put(VALUE_FIELD, "xueValue2");

            mContentResolver.insert(mUri,t);
        }

        private Cursor testQuery() {

            Cursor resultCursor = mContentResolver.query(mUri, null,
                    "*", null, null);
//            Cursor resultCursor = mContentResolver.query(mUri, null,
//                    "key4", null, null);
            Log.e(TAG, "[testQuery()] finish query and got resultCursor");
            return resultCursor;
        }
    }
}

