package ca.ubc.resess.vialinminitester;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import ca.ubc.resess.vialinminitester.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private Context context;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public String taintSource() {
        Editable t = binding.editTextText.getText();
        return t.toString();
    }

    public void taintTarget(String v) {
        postStringToUrl("http://www.google.com/generate_204", v != null ? v : "");
    }

    public void simpleTest() {
        taintTarget(taintSource());
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSimpleTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context = FirstFragment.this.getContext();
                simpleTest();
            }
        });

        binding.btnStoreSharedPref.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context = FirstFragment.this.getContext();
                storeSharedPref();
            }
        });

        binding.btnLoadSharedPref.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context = FirstFragment.this.getContext();
                loadSharedPref();
            }
        });

        binding.btnInstanceField1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context = FirstFragment.this.getContext();
                instanceFieldMapTest();
                taintTarget(instanceFieldMap.get("Field"));
            }
        });

        binding.btnInstanceField2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context = FirstFragment.this.getContext();
                instanceFieldTest(true);
                taintTarget(TextUtils.join(",", instanceField));
            }
        });

    }

    private String sharedPrefName = "taint_pref";
    private String sharedPrefItemName = "itemitem";

    private void storeSharedPref() {
        String taintSrc = taintSource();
        SharedPreferences sharedPref = requireActivity().getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(sharedPrefItemName, taintSrc);
        editor.apply();
    }

    private void loadSharedPref() {
        SharedPreferences sharedPref = requireActivity().getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE);
        String v = sharedPref.getString(sharedPrefItemName, "nonono");
        taintTarget(v);
    }

    private Map<String, String> instanceFieldMap = new HashMap<>();

    private void instanceFieldMapTest() {
        instanceFieldMap = new HashMap<>();
        String t = taintSource();
        instanceFieldMap.put("Field", t);
    }

    private String[] instanceField = new String[]{"1", "2", "3"};


    private void instanceFieldTest(boolean doTaint) {
        instanceField = new String[]{"1", "2", "3"};
        String t = taintSource();
        String[] array2 = new String[]{"2", "3"};
        if (doTaint) {
            array2 = instanceField;
        }
        array2[1] = t;
        Log.d("DEBUG", TextUtils.join(",", instanceField));
        Log.d("DEBUG", TextUtils.join(",", array2));
    }

    private void postStringToUrl(String url, String text) {
        new Thread(() -> {
            HttpURLConnection urlConnection = null;
            try {
                URL targetUrl = new URL(url);
                urlConnection = (HttpURLConnection) targetUrl.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "text/plain");
                urlConnection.setDoOutput(true);

                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());
                outputStreamWriter.write(text);
                outputStreamWriter.flush();

                int responseCode = urlConnection.getResponseCode();
                if (responseCode == 200) {
                    Log.i("PostStringToUrl", "Success: " + responseCode);
                } else {
                    Log.i("PostStringToUrl", "Failed: " + responseCode);
                }
                Log.d("Bench","Post String to URL: " + text);
            } catch (IOException exception) {
                exception.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}