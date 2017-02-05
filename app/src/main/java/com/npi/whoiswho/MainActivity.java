/*
Base code for interaction with the PandoraBots API extracted from:

https://github.com/zoraidacallejas/ConversationalInterface/tree/master/chapter7/TalkBot

This activity handles the main components of the Guess Who game. It contains:
 -The button that allows us to talk to the bot over the Internet.
 -The field where we can see what the speech recognizer has understood.
 -A table with all the characters and their features
 -A menu icon for the help and conversation history pages.
*/

package com.npi.whoiswho;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.os.Bundle;
import android.content.Context;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.npi.whoiswho.pandora.PandoraConnection;
import com.npi.whoiswho.pandora.PandoraErrorCode;
import com.npi.whoiswho.pandora.PandoraException;
import com.npi.whoiswho.voiceinterface.VoiceActivity;


public class MainActivity extends VoiceActivity {

    private static final String LOGTAG = "TALKBOT";

    private static Integer ID_PROMPT_QUERY = 0;    //Id chosen to identify the prompts that involve posing questions to the user
    private static Integer ID_PROMPT_INFO = 1;    //Id chosen to identify the prompts that involve only informing the user
    private long startListeningTime = 0; // To skip errors (see processAsrError method)

    private String host = "aiaas.pandorabots.com";
    private String userKey = "3223f25e9022d6b4291ea3f0ef4f4fe4";
    private String appId = "1409614146927";
    private String botName = "whoiswho";
    private ArrayList<String> conversation = new ArrayList<>();
    PandoraConnection pandoraConnection = new PandoraConnection(host, appId, userKey, botName);

    Locale spanish = new Locale("spa","ESP");

    private TextView pregunta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Set layout
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        myToolbar.setTitleTextColor(Color.WHITE);
        myToolbar.setLogo(R.mipmap.ic_launcher);
        myToolbar.setTitleMarginStart(50);

        //Initialize the speech recognizer and synthesizer
        initSpeechInputOutput(this);

        //Set up the speech button, history button and progress circle
        setSpeakButton();
        setCharacterTable();

        //Establece el stream de audio de media
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //Conecta el textView que nos indica qué ha entendido el reconocedor de voz
        pregunta = (TextView) findViewById(R.id.pregunta);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){

        switch(item.getItemId()){

            case R.id.help:
                Intent showHelp = new Intent(MainActivity.this, HelpActivity.class);
                startActivity(showHelp);
                return true;
            case R.id.history:
                Intent showHistory = new Intent(MainActivity.this, ConversationHistoryActivity.class);
                showHistory.putStringArrayListExtra("historyArray",conversation);
                startActivity(showHistory);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }

    }

    private void setSpeakButton() {
        this.setDefaultButtonAppearance();
        // gain reference to speak button
        Button speak = (Button) findViewById(R.id.speech_btn);
        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Start ASR
                indicateListening();
                startListening();
            }
        });
    }

    private void setCharacterTable() {

        // Obtenemos la tabla de personajes.
        TableLayout table = (TableLayout) findViewById(R.id.characters);

        // Para cada fila excepto la primera, que no sirve de nada tacharla.
        for(int i = 1 ; i < table.getChildCount() ; i++){

            // Obtenemos cada fila y definimos su onClick.
            final TableRow row = (TableRow) table.getChildAt(i);
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v){

                    TextView name = (TextView) row.getChildAt(0); // Tacharemos el nombre del personaje
                    // Si ya se estaba tachando, se desactiva al hacer click.
                    if((name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG) == name.getPaintFlags())
                        name.setPaintFlags(name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    else // Si no, se activa el tachado.
                        name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }

            });
        }
    }

    public void showRecordPermissionExplanation(){
        Toast.makeText(getApplicationContext(), "TalkBot needs your permission to access the microphone in order to perform speech recognition", Toast.LENGTH_SHORT).show();
    }

    public void onRecordAudioPermissionDenied(){
        Toast.makeText(getApplicationContext(), "Sorry, TalkBot cannot work without accessing the microphone", Toast.LENGTH_SHORT).show();
        System.exit(0);
    }

    private void startListening(){

        if(deviceConnectedToInternet()){
            try {

				/*Start listening, with the following default parameters:
					* Recognition model = Free form,
					* Number of results = 1 (we will use the best result to perform the search)
					*/
                startListeningTime = System.currentTimeMillis();
                listen(spanish, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, 1); //Start listening
            } catch (Exception e) {
                Log.e(LOGTAG, e.getMessage());
            }
        } else {
            Log.e(LOGTAG, "Device not connected to Internet");
        }
    }

    private void indicateListening() {
        Button button = (Button) findViewById(R.id.speech_btn); //Obtains a reference to the button
        button.setText(getResources().getString(R.string.speechbtn_listening)); //Changes the button's message to the text obtained from the resources folder
        button.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.speechbtn_listening),PorterDuff.Mode.MULTIPLY); //Changes the button's background to the color obtained from the resources folder
    }

    private void setDefaultButtonAppearance(){
        Button button = (Button) findViewById(R.id.speech_btn); //Obtains a reference to the button
        button.setText(getResources().getString(R.string.speechbtn_default)); //Changes the button's message to the text obtained from the resources folder
        button.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.speechbtn_default),PorterDuff.Mode.MULTIPLY);	//Changes the button's background to the color obtained from the resources folder
    }

    @Override
    public void processAsrError(int errorCode) {
        setDefaultButtonAppearance();

        //Possible bug in Android SpeechRecognizer: NO_MATCH errors even before the ASR
        // has even tried to recognized. We have adopted the solution proposed in:
        // http://stackoverflow.com/questions/31071650/speechrecognizer-throws-onerror-on-the-first-listening
        long duration = System.currentTimeMillis() - startListeningTime;
        if (duration < 500 && errorCode == SpeechRecognizer.ERROR_NO_MATCH) {
            Log.e(LOGTAG, "Doesn't seem like the system tried to listen at all. duration = " + duration + "ms. Going to ignore the error");
            stopListening();
        }
        else {
            String errorMsg = "";
            switch (errorCode) {
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMsg = "Audio recording error";
                case SpeechRecognizer.ERROR_CLIENT:
                    errorMsg = "Unknown client side error";
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    errorMsg = "Insufficient permissions";
                case SpeechRecognizer.ERROR_NETWORK:
                    errorMsg = "Network related error";
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMsg = "Network operation timed out";
                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMsg = "No recognition result matched";
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    errorMsg = "RecognitionService busy";
                case SpeechRecognizer.ERROR_SERVER:
                    errorMsg = "Server sends error status";
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMsg = "No speech input";
                default:
                    errorMsg = ""; //Another frequent error that is not really due to the ASR, we will ignore it
            }
            if (errorMsg != "") {
                this.runOnUiThread(new Runnable() { //Toasts must be in the main thread
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Speech recognition error", Toast.LENGTH_LONG).show();
                    }
                });

                Log.e(LOGTAG, "Error when attempting to listen: " + errorMsg);
                try { speak(errorMsg,"spa","ESP", ID_PROMPT_INFO); } catch (Exception e) { Log.e(LOGTAG, "TTS not accessible"); }
            }
        }
    }

    @Override
    public void processAsrReadyForSpeech() {}

    @Override
    public void processAsrResults(ArrayList<String> nBestList, float[] nBestConfidences) {
        setDefaultButtonAppearance();

        if(nBestList!=null){
            if(nBestList.size()>0){
                String userQuery = nBestList.get(0); //We will use the best result
                pregunta.setText("He entendido: " + userQuery);
                //Guarda la línea bien formateada en la conversación.
                userQuery = userQuery.substring(0,1).toUpperCase()+ userQuery.substring(1);
                conversation.add("Tú: "+userQuery+".");
                //Quita los acentos del string obtenido
                userQuery = RemoveAccents(userQuery);

                try {
                    String response = pandoraConnection.talk(userQuery); //Query to pandorabots
                    processBotResults(response); //Process the bot response
                } catch (PandoraException e) {
                    processBotErrors(e.getErrorCode());
                }
            }
        }

    }

    private void processBotErrors(PandoraErrorCode errorCode){
        String errormsg="";

        switch(errorCode)
        {
            case ID: //Problem with app id, user key or robot name (parameters required by Pandorabots)
                errormsg = getResources().getString(R.string.iderror_prompt);
                break;

            case NOMATCH: //There is no response available for the query
                errormsg = getResources().getString(R.string.nomatch_prompt);
                break;

            case IDORHOST: //Problem with ID or the host name indicted for the Pandora service
                errormsg =  getResources().getString(R.string.iderror_prompt);
                break;

            case CONNECTION: //Internet connection error
                errormsg = getResources().getString(R.string.connectionerror_prompt);
                break;

            case PARSE: //Error parsing the robot response
                errormsg = getResources().getString(R.string.parseerror_prompt);
                break;

            default:
                errormsg = getResources().getString(R.string.connectionerror_prompt);
                break;
        }

        try {
            speak(errormsg, spanish, ID_PROMPT_INFO);
            conversation.add("Bot: "+errormsg+".");
            Log.e(LOGTAG, getResources().getString(R.string.connectionerror_prompt));
        } catch (Exception e) {
            Log.e(LOGTAG, "The message '" + errormsg + "' could not be synthesized");
        }

    }

    public void processBotResults(String result){

        Log.d(LOGTAG, "Response, contents of that: "+result);

        result = removeTags(result);
        conversation.add("Bot: "+result);
        try {
            speak(result,spanish,ID_PROMPT_INFO);

        } catch (Exception e) {
            pregunta.setText("Error: " + e);
            Log.e(LOGTAG, "The message '"+result+"' could not be synthesized");
        }
    }

    private String removeTags(String string) {
        Pattern REMOVE_TAGS = Pattern.compile("<.+?>");

        if (string == null || string.length() == 0) {
            return string;
        }

        Matcher m = REMOVE_TAGS.matcher(string);
        return m.replaceAll("");
    }

    public boolean deviceConnectedToInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return (activeNetwork != null && activeNetwork.isConnectedOrConnecting());
    }

    public String RemoveAccents(String original) {

        // Cadena con los caracteres acentuados, ñ y diéresis.
        String acentos = "áéíóúüÁÉÍÓÚÜ";
        // Cadena de caracteres sin acentos, ñ o diéresis.
        String sin_acentos = "aeiouuAEIOUU";

        String resultado = original;

        for (int i=0; i<acentos.length(); i++) {

            resultado = resultado.replace(acentos.charAt(i), sin_acentos.charAt(i));
        }

        return resultado;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdown();
    }

    @Override
    public void onTTSDone(String uttId) {
        if(uttId.equals(ID_PROMPT_QUERY.toString()))
            startListening();

    }

    @Override
    public void onTTSError(String uttId) {
        Log.e(LOGTAG, "TTS error");
    }

    @Override
    public void onTTSStart(String uttId) {
        Log.e(LOGTAG, "TTS starts speaking");
    }
}
