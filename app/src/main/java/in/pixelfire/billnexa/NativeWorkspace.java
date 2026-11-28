package in.pixelfire.billnexa;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/** Native Android workspace. All data is supplied by the native local store. */
final class NativeWorkspace extends LinearLayout {
    interface Actions { void run(String command); }
    private static final int NAVY=0xff07162e, GOLD=0xfff5c65e, INK=0xff10223e, MUTED=0xff53627a, BG=0xffeef1f6, WHITE=Color.WHITE, BORDER=0xffdfe5ee;
    private final Activity activity;
    private final Actions actions;
    private LinearLayout body,bar,results,filters;
    private JSONObject data=new JSONObject();
    private String screen="",query="",filter="all",sort="recent";
    private int limit=40;
    NativeWorkspace(Activity a,Actions callback){
        super(a);activity=a;actions=callback;setOrientation(VERTICAL);setBackgroundColor(BG);setFocusableInTouchMode(true);
    }
    boolean supports(String s){return "home".equals(s)||"invoices".equals(s)||"ledger".equals(s)||"settings".equals(s);}
    void keyboardVisible(boolean visible){if(bar!=null)bar.setVisibility(visible?View.GONE:View.VISIBLE);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private void surface(View v,int color,int radius,int stroke){v.setBackground(shape(color,radius,stroke));}
    private void clickable(View v,int color,int radius,Runnable click){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),shape(color,radius,0),null));v.setOnClickListener(w->click.run());v.setFocusable(true);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(activity);l.setOrientation(VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(activity);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void add(LinearLayout p,View v){p.addView(v,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));}
    private void weight(LinearLayout p,View v){p.addView(v,new LayoutParams(0,LayoutParams.WRAP_CONTENT,1));}
    private void gap(LinearLayout p,int n){View v=new View(activity);if(p.getOrientation()==HORIZONTAL)p.addView(v,new LayoutParams(dp(n),1));else p.addView(v,new LayoutParams(1,dp(n)));}
    private void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
    private void route(String s){actions.run("go("+JSONObject.quote(s)+")");}
    private void openBill(String id){actions.run("openInvoice("+JSONObject.quote(id)+")");}
    private TextView button(String label,boolean primary,Runnable action){
        TextView t=text(label,14,primary?NAVY:INK,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(8),dp(10),dp(8),dp(10));clickable(t,primary?GOLD:0xffe6edf6,13,action);return t;
    }
    private LinearLayout card(LinearLayout parent){LinearLayout c=col();pad(c,15);surface(c,WHITE,16,BORDER);add(parent,c);gap(parent,12);return c;}
    private void heading(LinearLayout parent,String title,String sub){add(parent,text(title,25,INK,true));if(!sub.isEmpty()){gap(parent,4);add(parent,text(sub,13,MUTED,false));}gap(parent,14);}
    private void section(LinearLayout parent,String title){add(parent,text(title,18,INK,true));gap(parent,10);}
    private JSONArray rows(){JSONArray a=data.optJSONArray("rows");return a==null?new JSONArray():a;}
    private ArrayList<JSONObject> records(){ArrayList<JSONObject> list=new ArrayList<>();JSONArray a=rows();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)list.add(o);}return list;}
    private void pair(LinearLayout p,String l1,boolean primary,Runnable a,String l2,Runnable b){LinearLayout r=row();weight(r,button(l1,primary,a));gap(r,8);weight(r,button(l2,false,b));add(p,r);gap(p,8);}
    private void metric(LinearLayout parent,String label,String amount,int color){
        LinearLayout c=col();pad(c,12);surface(c,color,14,0);add(c,text(label,12,MUTED,true));gap(c,6);add(c,text(amount,20,INK,true));weight(parent,c);
    }
    private void metrics(String fourth){
        LinearLayout first=row();metric(first,"Total sales",data.optString("sales"),0xffe2ecfb);gap(first,8);metric(first,"Received",data.optString("paid"),0xffddf5e9);add(body,first);gap(body,8);
        LinearLayout second=row();metric(second,"Outstanding",data.optString("due"),0xffffede4);gap(second,8);metric(second,fourth,"home".equals(screen)?data.optString("count","0"):String.valueOf(rows().length()),0xfffff3d7);add(body,second);gap(body,14);
    }
    void show(JSONObject snapshot){
        data=snapshot;String next=snapshot.optString("screen");
        if(!next.equals(screen)){query="";filter="all";sort="recent";limit=40;}
        screen=next;results=null;removeAllViews();
        LinearLayout header=row();pad(header,12);header.setBackgroundColor(NAVY);
        ImageView logo=new ImageView(activity);logo.setImageResource(R.drawable.billnexa_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);header.addView(logo,new LayoutParams(dp(48),dp(48)));gap(header,8);
        LinearLayout brand=col();add(brand,text("BILLNEXA",22,GOLD,true));add(brand,text("PREMIUM BUSINESS BILLING",9,0xfff7e0a6,true));weight(header,brand);
        TextView create=button("+ New bill",true,()->actions.run("newInvoice()"));header.addView(create,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));add(this,header);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=col();pad(body,14);scroll.addView(body,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));addView(scroll,new LayoutParams(LayoutParams.MATCH_PARENT,0,1));
        if("home".equals(screen))home();else if("invoices".equals(screen))history();else if("ledger".equals(screen))ledger();else settings();
        TextView credit=text("Development by Ravi Vadhiya",11,MUTED,false);credit.setGravity(Gravity.CENTER);credit.setMinHeight(dp(48));credit.setContentDescription("Development by Ravi Vadhiya, open Instagram profile");credit.setOnClickListener(v->actions.run("openDeveloperProfile()"));add(body,credit);
        bar=row();bar.setBackgroundColor(NAVY);bar.setPadding(dp(4),dp(5),dp(4),dp(5));
        String[] labels={"Home","Bills","Customers","Items","More"},routes={"home","invoices","customers","products","settings"},icons={"⌂","▤","♙","▦","☷"};
        for(int i=0;i<labels.length;i++){final String target=routes[i];boolean active=screen.equals(target)||("ledger".equals(screen)&&"customers".equals(target));TextView t=text(icons[i]+"\n"+labels[i],13,active?NAVY:WHITE,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(58));clickable(t,active?GOLD:NAVY,12,()->route(target));weight(bar,t);}add(this,bar);
    }
    private void home(){
        heading(body,"Business overview",data.optString("business").isEmpty()?"Manage your business, simply.":data.optString("business"));
        LinearLayout hero=col();pad(hero,20);surface(hero,0xff17365c,18,0);add(hero,text("YOUR BILLING WORKSPACE",11,GOLD,true));gap(hero,8);add(hero,text("Make every sale count.",24,WHITE,true));gap(hero,8);add(hero,text("Create invoices, manage stock and track payments.",13,0xffd9e4f4,false));gap(hero,12);add(hero,button("+ Create invoice",true,()->actions.run("newInvoice()")));add(body,hero);gap(body,14);
        metrics("Total bills");
        LinearLayout quick=card(body);section(quick,"Quick actions");
        pair(quick,"Product Bill Studio",true,()->route("productstudio"),"New invoice",()->actions.run("newInvoice()"));
        pair(quick,"Customers",false,()->route("customers"),"Products & services",()->route("products"));
        pair(quick,"Inventory & stock",false,()->route("inventory"),"Sales reports",()->route("reports"));
        pair(quick,"Quotations",false,()->route("quotes"),"Customer ledger",()->route("ledger"));
        add(quick,button("Business profile",false,()->route("profile")));
        LinearLayout recent=card(body);section(recent,"Recent bills");ArrayList<JSONObject> list=records();if(list.isEmpty())add(recent,text("No bills yet. Create your first bill.",13,MUTED,false));for(JSONObject o:list)billRow(recent,o);gap(recent,8);add(recent,button("View all bills",false,()->route("invoices")));
    }
    private void billRow(LinearLayout p,JSONObject o){
        LinearLayout r=row();pad(r,12);surface(r,0xfff7f9fc,12,BORDER);LinearLayout left=col();add(left,text(o.optString("number"),15,INK,true));gap(left,4);add(left,text(o.optString("customer"),14,INK,false));add(left,text(o.optString("date")+" · "+o.optString("status"),12,MUTED,false));weight(r,left);gap(r,8);LinearLayout right=col();TextView amount=text(o.optString("total"),15,INK,true);amount.setGravity(Gravity.END);add(right,amount);TextView view=text("View bill ›",12,0xff956315,true);view.setGravity(Gravity.END);add(right,view);r.addView(right,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));r.setOnClickListener(v->openBill(o.optString("id")));r.setFocusable(true);add(p,r);gap(p,8);
    }
    private void searchBox(String hint){
        EditText input=new EditText(activity);input.setSingleLine(true);input.setTextSize(15);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHint(hint);input.setContentDescription(hint);input.setText(query);input.setPadding(dp(13),dp(8),dp(13),dp(8));input.setMinHeight(dp(50));surface(input,WHITE,12,BORDER);add(body,input);gap(body,10);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();limit=40;refresh();}public void afterTextChanged(Editable e){}});
    }
    private void filterButtons(String[] options){
        filters=row();for(String item:options){TextView b=button(item.equals("all")?"All":item,item.equals(filter),()->{filter=item;limit=40;for(int i=0;i<filters.getChildCount();i++){View v=filters.getChildAt(i);if(v instanceof TextView){TextView t=(TextView)v;String label=t.getText().toString();boolean sel=label.equals(filter)||label.equals("All")&&filter.equals("all");t.setTextColor(sel?NAVY:INK);surface(t,sel?GOLD:0xffe6edf6,13,0);}}refresh();});b.setTextSize(12);if(filters.getChildCount()>0)gap(filters,5);weight(filters,b);}add(body,filters);gap(body,12);
    }
    private void history(){heading(body,"Bill history","Search, filter and manage your saved bills.");searchBox("Search bill number or customer");filterButtons(new String[]{"all","Paid","Partial","Unpaid"});results=col();add(body,results);refresh();}
    private void ledger(){
        heading(body,"Customer ledger","Sales, payments and outstanding balances");metrics("Customers");add(body,button("Manage customers",false,()->route("customers")));gap(body,12);searchBox("Search name, phone or GSTIN");filterButtons(new String[]{"all","Outstanding"});
        LinearLayout sortRow=row();weight(sortRow,text("Sort by",13,MUTED,true));Spinner spinner=new Spinner(activity);String[] labels={"Recent","Highest due","Name A–Z"};ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels);spinner.setAdapter(adapter);spinner.setSelection("due".equals(sort)?1:"name".equals(sort)?2:0);spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){sort=pos==1?"due":pos==2?"name":"recent";limit=40;refresh();}});weight(sortRow,spinner);add(body,sortRow);gap(body,10);results=col();add(body,results);refresh();
    }
    private boolean matches(JSONObject o,String... fields){String q=query.trim().toLowerCase(Locale.ROOT);for(String k:fields)if(o.optString(k).toLowerCase(Locale.ROOT).contains(q))return true;return false;}
    private void refresh(){
        if(results==null)return;results.removeAllViews();ArrayList<JSONObject> list=records();boolean ledger="ledger".equals(screen);ArrayList<JSONObject> visible=new ArrayList<>();
        for(JSONObject o:list){if(ledger){if((!"Outstanding".equals(filter)||o.optBoolean("outstanding"))&&matches(o,"name","phone","gstin","address"))visible.add(o);}else if(("all".equals(filter)||filter.equals(o.optString("status")))&&matches(o,"number","customer"))visible.add(o);}
        if(ledger)Collections.sort(visible,(a,b)->"due".equals(sort)?Double.compare(b.optDouble("dueNumber"),a.optDouble("dueNumber")):"name".equals(sort)?a.optString("name").compareToIgnoreCase(b.optString("name")):b.optString("last").compareTo(a.optString("last")));
        add(results,text(visible.size()+(ledger?" customers":" bills"),13,MUTED,true));gap(results,9);
        if(visible.isEmpty())add(card(results),text(ledger?"No matching customers.":"No matching bills.",14,MUTED,false));
        for(int i=0;i<Math.min(limit,visible.size());i++){JSONObject o=visible.get(i);if(ledger){LinearLayout c=card(results);add(c,text(o.optString("name"),17,INK,true));add(c,text(o.optString("phone")+" · "+o.optInt("count")+" bills",12,MUTED,false));if(!o.optString("address").isEmpty())add(c,text(o.optString("address"),12,MUTED,false));gap(c,8);add(c,text("Sales  "+o.optString("sales")+"    Received  "+o.optString("paid"),13,INK,false));gap(c,5);add(c,text("Outstanding  "+o.optString("due"),16,o.optBoolean("outstanding")?0xffa12b13:0xff08784f,true));}else billRow(results,o);}
        if(visible.size()>limit)add(results,button("Load more",false,()->{limit+=40;refresh();}));
    }
    private void settingsGroup(String title,String[][] items){
        LinearLayout c=card(body);section(c,title);
        for(String[] item:items){final String target=item[2];LinearLayout r=row();r.setPadding(dp(4),dp(11),dp(4),dp(11));r.setMinimumHeight(dp(64));LinearLayout l=col();add(l,text(item[0],15,INK,true));gap(l,3);add(l,text(item[1],12,MUTED,false));weight(r,l);gap(r,8);addArrow(r);clickable(r,WHITE,9,()->route(target));add(c,r);View line=new View(activity);line.setBackgroundColor(BORDER);c.addView(line,new LayoutParams(LayoutParams.MATCH_PARENT,dp(1)));}
    }
    private void addArrow(LinearLayout r){TextView t=text("›",25,0xff956315,true);r.addView(t,new LayoutParams(dp(22),LayoutParams.WRAP_CONTENT));}
    private void settings(){
        heading(body,"Settings","Manage your business, privacy and app preferences.");
        LinearLayout c=card(body);surface(c,0xff17365c,16,0);add(c,text(data.optString("business").isEmpty()?"Your business":data.optString("business"),21,WHITE,true));gap(c,5);add(c,text("BillNexa · Version 3.0",13,GOLD,false));
        settingsGroup("Business",new String[][]{{"Business profile","Shop name, logo and GSTIN","profile"},{"Saved signature","Draw or upload JPG / PNG signature","signature"},{"Inventory & stock","Product quantities","inventory"},{"Sales reports","Business performance","reports"},{"Quotations","Create estimates","quotes"},{"Customer ledger","Outstanding balances","ledger"}});
        settingsGroup("Data & security",new String[][]{{"Backup & restore","Export and import local records","backupsettings"},{"Privacy & data controls","Manage or delete local data","datacontrols"}});
        settingsGroup("Legal & support",new String[][]{{"Privacy Policy","How information is handled","privacy"},{"Terms of Use","Usage rules","terms"},{"Help & FAQ","Common questions","help"},{"Contact Support","Email the developer","support"},{"About BillNexa","App information","about"}});
    }
}
