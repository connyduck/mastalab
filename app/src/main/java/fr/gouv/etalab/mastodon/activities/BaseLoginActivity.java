/* Copyright 2017 Thomas Schneider
 *
 * This file is a part of Mastalab
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastalab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastalab; if not,
 * see <http://www.gnu.org/licenses>. */
package fr.gouv.etalab.mastodon.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;;
import java.util.HashMap;

import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.asynctasks.UpdateAccountInfoAsyncTask;
import fr.gouv.etalab.mastodon.client.Entities.HttpsConnection;
import fr.gouv.etalab.mastodon.helper.Helper;

import static fr.gouv.etalab.mastodon.helper.Helper.changeDrawableColor;


/**
 * Created by Thomas on 23/04/2017.
 * Login activity class which handles the connection
 */

public abstract class BaseLoginActivity extends AppCompatActivity {

    private String client_id;
    private String client_secret;
    private TextView login_two_step;
    private static boolean client_id_for_webview = false;
    private String instance;
    private AutoCompleteTextView login_instance;
    private EditText login_uid;
    private EditText login_passwd;
    boolean isLoadingInstance = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installProviders();
        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
        int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
        if( theme == Helper.THEME_LIGHT){
            setTheme(R.style.AppTheme);
        }else {
            setTheme(R.style.AppThemeDark);
        }
        setContentView(R.layout.activity_login);

        if( theme == Helper.THEME_DARK) {
            changeDrawableColor(getApplicationContext(), R.drawable.mastodon_icon, R.color.mastodonC2);
        }else {
            changeDrawableColor(getApplicationContext(), R.drawable.mastodon_icon, R.color.mastodonC3);
        }
        final Button connectionButton = findViewById(R.id.login_button);
        login_instance = findViewById(R.id.login_instance);
        login_uid = findViewById(R.id.login_uid);
        login_passwd = findViewById(R.id.login_passwd);


        if( theme == Helper.THEME_LIGHT) {
           connectionButton.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.white));
        }
        login_instance.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
            @Override
            public void afterTextChanged(Editable s) {
                if( s.length() > 2 && !isLoadingInstance){
                    final String action = "/instances/search";
                    final HashMap<String, String> parameters = new HashMap<>();
                    parameters.put("q", s.toString().trim());
                    parameters.put("count", String.valueOf(5));
                    parameters.put("name", String.valueOf(true));
                    isLoadingInstance = true;
                    new Thread(new Runnable(){
                        @Override
                        public void run() {
                            try {
                                final String response = new HttpsConnection().get("https://instances.social/api/1.0" + action,  parameters, Helper.THEKINRAR_SECRET_TOKEN );
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        isLoadingInstance = false;
                                        String[] instances;
                                        try {
                                            JSONObject jsonObject = new JSONObject(response);
                                            JSONArray jsonArray = jsonObject.getJSONArray("instances");
                                            if( jsonArray != null){
                                                instances = new String[jsonArray.length()];
                                                for(int i = 0 ; i < jsonArray.length() ; i++){
                                                    instances[i] = jsonArray.getJSONObject(i).get("name").toString();
                                                }
                                            }else {
                                                instances = new String[]{};
                                            }
                                            login_instance.setAdapter(null);
                                            ArrayAdapter<String> adapter =
                                                    new ArrayAdapter<>(BaseLoginActivity.this, android.R.layout.simple_list_item_1, instances);
                                            login_instance.setAdapter(adapter);
                                            if( login_instance.hasFocus() && !BaseLoginActivity.this.isFinishing())
                                                login_instance.showDropDown();

                                        } catch (JSONException ignored) {isLoadingInstance = false;}
                                    }
                                });

                            } catch (IOException e) {
                                isLoadingInstance = false;
                                e.printStackTrace();
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            } catch (KeyManagementException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            }
        });


        connectionButton.setEnabled(false);
        login_two_step = findViewById(R.id.login_two_step);
        login_two_step.setVisibility(View.GONE);
        login_two_step.setPaintFlags(login_two_step.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        login_two_step.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                client_id_for_webview = true;
                retrievesClientId();
            }
        });

        login_instance.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                connectionButton.setEnabled(false);
                login_two_step.setVisibility(View.INVISIBLE);
                if (!hasFocus) {
                    retrievesClientId();
                }
            }
        });
    }

    protected abstract void installProviders();

    @Override
    protected void onResume(){
        super.onResume();
        Button connectionButton = findViewById(R.id.login_button);
        if (login_instance.getText() != null && login_instance.getText().toString().length() > 0 && client_id_for_webview) {
            connectionButton.setEnabled(false);
            client_id_for_webview = false;
            retrievesClientId();
        }
    }

    private void retrievesClientId(){
        final Button connectionButton = findViewById(R.id.login_button);
        try {
            instance =  URLEncoder.encode(login_instance.getText().toString().trim(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(BaseLoginActivity.this,R.string.client_error, Toast.LENGTH_LONG).show();
        }
        final String action = "/api/v1/apps";
        final HashMap<String, String> parameters = new HashMap<>();
        parameters.put(Helper.CLIENT_NAME, Helper.CLIENT_NAME_VALUE);
        parameters.put(Helper.REDIRECT_URIS, client_id_for_webview?Helper.REDIRECT_CONTENT_WEB:Helper.REDIRECT_CONTENT);
        parameters.put(Helper.SCOPES, Helper.OAUTH_SCOPES);
        parameters.put(Helper.WEBSITE, Helper.WEBSITE_VALUE);

        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    final String response = new HttpsConnection().post("https://" + instance + action,  parameters, null );
                    runOnUiThread(new Runnable() {
                      public void run() {
                          JSONObject resobj;
                          try {
                              resobj = new JSONObject(response);
                              client_id = resobj.get(Helper.CLIENT_ID).toString();
                              client_secret = resobj.get(Helper.CLIENT_SECRET).toString();

                              String id = resobj.get(Helper.ID).toString();
                              SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                              SharedPreferences.Editor editor = sharedpreferences.edit();
                              editor.putString(Helper.CLIENT_ID, client_id);
                              editor.putString(Helper.CLIENT_SECRET, client_secret);
                              editor.putString(Helper.ID, id);
                              editor.apply();
                              connectionButton.setEnabled(true);
                              login_two_step.setVisibility(View.VISIBLE);
                              if( client_id_for_webview){
                                  Intent i = new Intent(BaseLoginActivity.this, WebviewConnectActivity.class);
                                  i.putExtra("instance", instance);
                                  startActivity(i);
                              }
                          } catch (JSONException e) {
                              e.printStackTrace();
                          }
                      }
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (KeyManagementException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        connectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectionButton.setEnabled(false);
                if( client_id_for_webview ){
                    client_id_for_webview = false;
                    retrievesClientId();
                    return;
                }

                final HashMap<String, String> parameters = new HashMap<>();
                SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                parameters.put(Helper.CLIENT_ID, sharedpreferences.getString(Helper.CLIENT_ID, null));
                parameters.put(Helper.CLIENT_SECRET, sharedpreferences.getString(Helper.CLIENT_SECRET, null));
                parameters.put("grant_type", "password");
                parameters.put("username",login_uid.getText().toString().trim());
                parameters.put("password",login_passwd.getText().toString().trim());
                parameters.put("scope"," read write follow");

                new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            final String response = new HttpsConnection().post("https://" + instance + "/oauth/token",  parameters, null );
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    JSONObject resobj;
                                    try {
                                        resobj = new JSONObject(response);
                                        String token = resobj.get("access_token").toString();
                                        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                                        SharedPreferences.Editor editor = sharedpreferences.edit();
                                        editor.putString(Helper.PREF_KEY_OAUTH_TOKEN, token);
                                        editor.apply();
                                        //Update the account with the token;
                                        new UpdateAccountInfoAsyncTask(BaseLoginActivity.this, token, instance).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } catch (IOException e) {
                            connectionButton.setEnabled(true);
                            Toast.makeText(getApplicationContext(),R.string.toast_error_login,Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (KeyManagementException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
            startActivity(intent);
        }else if(id == R.id.action_privacy){
            Intent intent = new Intent(getApplicationContext(), PrivacyActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }
}