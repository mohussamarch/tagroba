package app.masroufy.personal;

import android.Manifest;
import android.database.Cursor;
import android.provider.Telephony;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import java.util.*;

@CapacitorPlugin(name="SmsInbox", permissions={
    @Permission(alias="automaticSms", strings={Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS})
})
public class SmsInboxPlugin extends Plugin {
    private String uid(PluginCall call) {
        String uid = call.getString("uid", "");
        if (uid.isEmpty() || uid.length()>128) throw new IllegalArgumentException();
        return uid;
    }
    private Set<String> senders(PluginCall call) throws Exception {
        JSArray input = call.getArray("senders", new JSArray());
        if (input.length()==0 || input.length()>10) throw new IllegalArgumentException();
        Set<String> values = new HashSet<>();
        for(int i=0;i<input.length();i++) {
            String value=input.getString(i).trim();
            if(value.isEmpty()||value.length()>50)throw new IllegalArgumentException();
            values.add(value);
        }
        return values;
    }
    private boolean granted() { return getPermissionState("automaticSms")==PermissionState.GRANTED; }
    @PluginMethod public void enable(PluginCall call) {
        try { uid(call); senders(call); }
        catch(Exception error) { call.reject("اكتب أسماء مرسلي البنك (حتى 10)"); return; }
        if(!granted()) { requestPermissionForAlias("automaticSms",call,"enabledPermission"); return; }
        enabledPermission(call);
    }
    @PermissionCallback private void enabledPermission(PluginCall call) {
        if(!granted()) { call.reject("إذن الرسائل غير متاح. فعّله من أذونات أندرويد أو استخدم اللصق."); return; }
        execute(call, store -> {store.configure(uid(call),senders(call));return store.snapshot(uid(call),true,false);});
    }
    @PluginMethod public void disable(PluginCall call) {
        execute(call, store -> {store.disable(uid(call));return store.snapshot(uid(call),granted(),false);});
    }
    @PluginMethod public void acknowledge(PluginCall call) {
        execute(call, store -> {store.acknowledge(uid(call),call.getArray("ids",new JSArray()));return store.snapshot(uid(call),granted(),false);});
    }
    @PluginMethod public void sync(PluginCall call) {
        execute(call, store -> {
            String owner=uid(call);
            // Permission checks never open Android's permission dialog automatically.
            boolean more=store.enabled(owner)&&granted()&&catchUp(store,owner);
            return store.snapshot(owner,granted(),more);
        });
    }
    private boolean catchUp(SmsInboxStore store,String owner) {
        Set<String> senders=store.senders();
        if(senders.isEmpty())return false;
        List<String> args=new ArrayList<>(Arrays.asList(Long.toString(store.cursorDate()),Long.toString(store.cursorDate()),Long.toString(store.cursorId())));
        List<String> matches=new ArrayList<>();
        for(String sender:senders){matches.add("address = ? COLLATE NOCASE");args.add(sender);}
        String selection="(date > ? OR (date = ? AND _id > ?)) AND ("+String.join(" OR ",matches)+")";
        try(Cursor rows=getContext().getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
            new String[]{"_id","address","body","date","date_sent"},selection,args.toArray(new String[0]),"date ASC, _id ASC")) {
            int processed=0;
            if(rows!=null)while(rows.moveToNext()) {
                if(processed++>=500)return true;
                long date=rows.getLong(3), sent=rows.getLong(4);
                store.enqueue(owner,rows.getString(1),sent>0?sent:date,rows.getString(2));
                // Advance only after durable enqueue or intentional safety filtering.
                store.checkpoint(date,rows.getLong(0));
            }
        }
        return false;
    }
    private interface Operation { JSObject run(SmsInboxStore store) throws Exception; }
    private void execute(PluginCall call,Operation operation) {
        getBridge().execute(()->{
            synchronized(SmsInboxStore.LOCK) {
                try(SmsInboxStore store=new SmsInboxStore(getContext())) {call.resolve(operation.run(store));}
                catch(SecurityException error) {call.reject("إذن الرسائل اتسحب. راجع أذونات أندرويد؛ الرسائل المعلقة محفوظة.");}
                catch(Exception error) {call.reject("تعذر تحديث رسائل البنك. جرّب تاني؛ لم نحذف الرسائل المعلقة.");}
            }
        });
    }
}
