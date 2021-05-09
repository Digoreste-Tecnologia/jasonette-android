package com.jasonette.seed.Action;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.jasonette.seed.Core.JasonViewActivity;
import com.jasonette.seed.Helper.JasonHelper;
import com.jasonette.seed.Launcher.Launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JasonGlobalAction {
    public Object[] addToArray(Object[] arr, Object toInsert) {
        int i;
        Object[] array = new Object[arr.length + 1];
        for (i = 0; i < arr.length; i++)
            array[i] = arr[i];
        array[arr.length] = toInsert;

        return array;
    }

    public void reset(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {

        /********************

         The following resets a global variable named "db".
         When a variable is reset, the key itself gets destroyed, so when you check ('db' in $global), it will return false

         {
             "type": "$global.reset",
             "options": {
                 "items": ["db"]
             }
         }

         ********************/

        try {
            SharedPreferences pref = context.getSharedPreferences("global", 0);
            SharedPreferences.Editor editor = pref.edit();



            JSONObject options = action.getJSONObject("options");
            if(options.has("items")){
                JSONArray items = options.getJSONArray("items");
                for (int i=0; i<items.length(); i++) {
                    String item = items.getString(i);
                    editor.remove(item);
                    ((Launcher)context.getApplicationContext()).resetGlobal(item);
                }
                editor.commit();
            }

            // Execute next
            JasonHelper.next("success", action, ((Launcher)context.getApplicationContext()).getGlobal(), event, context);

        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }

    }


    public void set(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {

        /********************

         The following sets a global variable named "db".

         {
             "type": "$global.set",
             "options": {
                 "db": ["a", "b", "c", "d"]
             }
         }

         Once set, you can access them through template expressions from ANYWHERE within the app, like this:

         {
             "items": {
                 "{{#each $global.db}}": {
                     "type": "label",
                     "text": "{{this}}"
                 }
             }
         }

         ********************/

        try {
            SharedPreferences pref = context.getSharedPreferences("global", 0);
            SharedPreferences.Editor editor = pref.edit();

            JSONObject options = action.getJSONObject("options");



            Iterator<String> keysIterator = options.keys();
            while (keysIterator.hasNext()) {
                String key = (String) keysIterator.next();
                Object val = options.get(key);
                editor.putString(key, val.toString());
                ((Launcher)context.getApplicationContext()).setGlobal(key, val);
            }
            editor.commit();

            // Execute next
            JasonHelper.next("success", action, ((Launcher)context.getApplicationContext()).getGlobal(), event, context);

        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    public void push(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {
        try {
            SharedPreferences pref = context.getSharedPreferences("global", 0);
            SharedPreferences.Editor editor = pref.edit();
            JSONObject globalContext = ((Launcher)context.getApplicationContext()).getGlobal();

            Boolean isPassive = action.has("passive");
            Boolean isPassiveEnabled = false;

            if (isPassive) {
                isPassiveEnabled = action.getBoolean("passive");
            }

            // {{$input.variable}} pattern
            String inputPattern = "\\{\\{\\$input\\.(.+)\\}\\}";
            Pattern inputRegex = Pattern.compile(inputPattern);
            // get local variables reference
            JSONObject localRef = ((JasonViewActivity) context).model.var;

            Iterator<String> keysIterator = options.keys();
            while (keysIterator.hasNext()) {
                String key = (String) keysIterator.next();
                try {
                    //treat the raw value as unparsed item in order to parse $input.variable values
                    JSONObject unparsedItem = options.getJSONObject(key);
                    JSONObject newItem = new JSONObject();

                    Iterator<String> keysUnparsed = unparsedItem.keys();
                    while (keysUnparsed.hasNext()) {
                        String unKey = keysUnparsed.next();
                        String unValue = unparsedItem.getString(unKey);

                        Matcher isInput = inputRegex.matcher(unValue);

                        if (isInput.find()) {
                            String varKey = isInput.group(1);
                            if(localRef.has(varKey)) {
                                // using updated values from local variables
                                newItem.put(unKey, localRef.getString(varKey));
                            } else {
                                newItem.put(unKey, "");
                            }
                        } else {
                            newItem.put(unKey, unValue);
                        }
                    }

                    JSONArray existingArray = globalContext.getJSONArray(key);
                    int JSONlength = existingArray.length();
                    //passive mode = don't push if array already exist.
                    if(!isPassiveEnabled) {
                        newItem.put("_id", JSONlength);

                        existingArray.put(newItem);
                        JSONObject lookFor_ID = existingArray.getJSONObject(0);
                        if(!lookFor_ID.has("_id")) {
                            JSONArray newJsonArray = new JSONArray();
                            for (int i = 0; i < JSONlength; i++) {
                                JSONObject item = existingArray.getJSONObject(i);
                                //update all array index
                                item.put("_id", i);
                                newJsonArray.put(item);
                            }
                            editor.putString(key, newJsonArray.toString());
                            ((Launcher)context.getApplicationContext()).setGlobal(key, newJsonArray);

                        } else {
                            editor.putString(key, existingArray.toString());
                            ((Launcher)context.getApplicationContext()).setGlobal(key, existingArray);
                        }

                    }

                    } catch(Exception e) {
                        //exception when the key is new
                        //in this case, we create a new array with the input values
                        //Log.d("warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        JSONArray newArray = new JSONArray();
                        JSONObject unparsedItem = options.getJSONObject(key);
                        JSONObject newItem = new JSONObject();
                        newItem.put("_id", 0);

                        Iterator<String> keysUnparsed = unparsedItem.keys();
                        while (keysUnparsed.hasNext()) {
                            String unKey = keysUnparsed.next();
                            String unValue = unparsedItem.getString(unKey);

                            Matcher isInput = inputRegex.matcher(unValue);

                            if (isInput.find()) {
                                String varKey = isInput.group(1);
                                if(localRef.has(varKey)) {
                                    // using updated values from local variables
                                    newItem.put(unKey, localRef.getString(varKey));
                                } else {
                                    newItem.put(unKey, "");
                                }
                            } else {
                                newItem.put(unKey, unValue);
                            }
                        }

                        newArray.put(newItem);
                        //Log.d("new object", newArray.toString() + "array to set");
                        editor.putString(key, newArray.toString());
                        ((Launcher)context.getApplicationContext()).setGlobal(key, newArray);
                    }

            }
            editor.commit();

            // Execute next
            JasonHelper.next("success", action, ((Launcher)context.getApplicationContext()).getGlobal(), event, context);

        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : pushed " + e.toString());
            JasonHelper.next("error", action, ((Launcher)context.getApplicationContext()).getGlobal(), event, context);
        }
    }

    public void splice(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {
        try {
            SharedPreferences pref = context.getSharedPreferences("global", 0);
            SharedPreferences.Editor editor = pref.edit();


            JSONObject globalContext = ((Launcher)context.getApplicationContext()).getGlobal();

            JSONObject options = action.getJSONObject("options");


            Iterator<String> keysIterator = options.keys();
            while (keysIterator.hasNext()) {
                String key = (String) keysIterator.next();
                try {
                    int removeItem = Integer.parseInt(options.getString(key));
                    JSONArray existingArray = globalContext.getJSONArray(key);
                    existingArray.remove(removeItem);

                    JSONArray newJsonArray = new JSONArray();
                    for (int i = 0; i < existingArray.length(); i++) {
                        JSONObject item = existingArray.getJSONObject(i);
                        //update all array index
                        item.put("_id", i);
                        newJsonArray.put(item);
                    }
                    editor.putString(key, newJsonArray.toString());
                    ((Launcher)context.getApplicationContext()).setGlobal(key, newJsonArray);
                } catch(Exception e) {
                    //exception when the key is new
                    //in this case, we create a new array with the input values
                    Log.d("warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                    JSONArray newArray = new JSONArray();
                    JSONObject newItem = options.getJSONObject(key);
                    newArray.put(newItem);
                    Log.d("new object", newArray.toString() + "array to set");
                    editor.putString(key, newArray.toString());
                    ((Launcher)context.getApplicationContext()).setGlobal(key, newArray);
                }

            }
            editor.commit();

            // Execute next
            JasonHelper.next("success", action, ((Launcher)context.getApplicationContext()).getGlobal(), event, context);

        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    public void update(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {
        try {
            SharedPreferences pref = context.getSharedPreferences("global", 0);
            SharedPreferences.Editor editor = pref.edit();

            JSONObject globalContext = ((Launcher)context.getApplicationContext()).getGlobal();

            JSONObject options = action.getJSONObject("options");

            Boolean passiveMode = false;
            if(action.has("passive")) {
                passiveMode = action.getBoolean("passive");
            }

            Iterator<String> keysIterator = options.keys();
            while (keysIterator.hasNext()) {
                String key = (String) keysIterator.next();
                try {
                    JSONObject updatedValue = options.getJSONObject(key);
                    int targetElement = Integer.parseInt(updatedValue.getString("_id"));

                    Log.d("ok let us see...", updatedValue.toString() + " ");
                    JSONArray existingArray = globalContext.getJSONArray(key);

                    JSONObject newValueToPush = new JSONObject();

                    String pointerPattern = "\\{\\{\\$(.+)\\}\\}";
                    Pattern regex = Pattern.compile(pointerPattern);
                    JSONObject oldObject = existingArray.getJSONObject(targetElement);
                    Iterator<String> newObjectIterator = oldObject.keys();
                    

                    while(newObjectIterator.hasNext()) {
                        String myKey = newObjectIterator.next();
                        if(updatedValue.has(myKey)) {
                            String valued = updatedValue.getString(myKey);
                            Matcher isPointer = regex.matcher(valued);
                            if(isPointer.find()) {
                                String fallbackValue = oldObject.getString(myKey);
                                newValueToPush.put(myKey, fallbackValue);
                            } else {
                                newValueToPush.put(myKey, valued);
                            }
                        }  else {
                            String fallbackValue = oldObject.getString(myKey);
                            newValueToPush.put(myKey, fallbackValue);
                        }
                    }
               int arrayLength = existingArray.length();
                    JSONArray newArray = new JSONArray();

                    for (int i = 0; i < arrayLength; i++) {
                        if (targetElement == i) {
                            newValueToPush.put("_id", i);
                            newArray.put(newValueToPush);
                        } else {
                            JSONObject existingObj = existingArray.getJSONObject(i);
                            existingObj.put("_id", i);
                            newArray.put(existingObj);
                        }
                    }

                    editor.putString(key, newArray.toString());
                    ((Launcher)context.getApplicationContext()).setGlobal(key, newArray);
                } catch(Exception e) {
                    //exception when the key is new
                    //in this case, we create a new array with the input values
                    Log.d("11warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                    JSONArray newArray = new JSONArray();
                    JSONObject newItem = options.getJSONObject(key);
                    newArray.put(newItem);
                    //Log.d("new object", newArray.toString() + "array to set");
                    editor.putString(key, newArray.toString());
                    ((Launcher)context.getApplicationContext()).setGlobal(key, newArray);
                }
            }
            editor.commit();

            // Execute next
            JasonHelper.next("success", action, ((Launcher)context.getApplicationContext()).getGlobal(), event, context);

        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }
    public void template_update(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {
        try {
            SharedPreferences pref = context.getSharedPreferences("global", 0);
            SharedPreferences.Editor editor = pref.edit();

            JSONObject globalContext = ((Launcher)context.getApplicationContext()).getGlobal();

            JSONObject options = action.getJSONObject("options");

            String target_list = action.getString("target");

            int pos = Integer.parseInt(action.getString("position"));

            JSONArray existingArray = globalContext.getJSONArray(target_list);

            JSONArray newArray = new JSONArray();

            if(existingArray.length() < pos) {
                JSONObject newObj = new JSONObject();
                Iterator<String> keysIterator = existingArray.getJSONObject(0).keys();
                while (keysIterator.hasNext()) {
                    String key = (String) keysIterator.next();
                    if(options.has(key)) {
                        newObj.put(key, options.getString(key));
                    } else {
                        newObj.put(key, existingArray.getJSONObject(pos).getString(key));
                    }
                }
                for(int i = 0; i < existingArray.length(); i++) {
                    if(i != pos) {
                        newArray.put(pos, existingArray.getJSONObject(i));
                    } else {
                        newArray.put(pos, newObj);
                    }
                }

                newArray.put(pos, newObj);

                Log.d("newval", newArray.toString());
                editor.putString(target_list, newArray.toString());
                ((Launcher)context.getApplicationContext()).setGlobal(target_list, newArray);
            }
            editor.commit();
            // Execute next
            JasonHelper.next("success", action, ((Launcher)context.getApplicationContext()).getGlobal(), event, context);
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

}
