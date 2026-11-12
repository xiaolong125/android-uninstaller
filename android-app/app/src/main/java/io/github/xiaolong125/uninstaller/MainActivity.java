package io.github.xiaolong125.uninstaller;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<AppEntry> apps = new ArrayList<>(), visible = new ArrayList<>();
    private final StringBuilder logs = new StringBuilder();
    private TextView shizukuState, result, count;
    private EditText search;
    private Button authorize, refresh;
    private ProgressBar progress;
    private ListView list;
    private AppAdapter adapter;
    private IUninstallService service;
    private Shizuku.UserServiceArgs serviceArgs;
    private boolean busy, binding, destroyed;
    private static final int GREEN = Color.rgb(8,127,117), INK = Color.rgb(28,45,57);
    private final Shizuku.OnBinderReceivedListener received = () -> main.post(this::updateConnection);
    private final Shizuku.OnBinderDeadListener died = () -> main.post(() -> { service = null; binding = false; updateConnection(); addLog("Shizuku 已断开，请重新启动后连接。"); });
    private final Shizuku.OnRequestPermissionResultListener permission = (request, grant) -> main.post(() -> {
        if (grant == PackageManager.PERMISSION_GRANTED) connect();
        else { setResult("未获 Shizuku 授权。可浏览应用；卸载需要授权。", true); updateConnection(); }
    });
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            main.post(() -> {
                if (destroyed) return;
                binding = false;
                service = IUninstallService.Stub.asInterface(binder);
                try {
                    int uid = service.getUid();
                    if (uid != 2000 && uid != 0) throw new SecurityException("服务权限异常：" + uid);
                    addLog("已连接卸载服务，UID=" + uid);
                } catch (Exception e) { service = null; setResult("连接服务失败：" + e.getMessage(), true); }
                updateConnection();
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { main.post(() -> { binding = false; service = null; updateConnection(); }); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(this, UninstallService.class))
            .daemon(false).processNameSuffix("uninstall").version(BuildConfig.VERSION_CODE).tag("user0-uninstall");
        buildUI();
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(died);
        Shizuku.addRequestPermissionResultListener(permission);
        updateConnection(); loadApps();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(INK);
        if (bold) view.setTypeface(null, Typeface.BOLD); return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private void buildUI() {
        LinearLayout root = column(); root.setBackgroundColor(Color.rgb(245,247,249));
        root.setPadding(dp(20), dp(12), dp(20), 0);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                left=bars.left; top=bars.top; right=bars.right; bottom=bars.bottom;
            } else { left=insets.getSystemWindowInsetLeft(); top=insets.getSystemWindowInsetTop(); right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom(); }
            v.setPadding(dp(20)+left,dp(12)+top,dp(20)+right,bottom); return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout title = new LinearLayout(this); title.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this); mark.setImageResource(io.github.xiaolong125.uninstaller.R.drawable.ic_launcher);
        title.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout heading = column(); heading.setPadding(dp(12),0,0,0);
        heading.addView(text("Android 卸载助手",22,true)); heading.addView(text("Shizuku 版 · 仅处理用户 0",12,false)); title.addView(heading);
        root.addView(title); space(root,12);
        LinearLayout card = column(); card.setPadding(dp(14),dp(10),dp(14),dp(10));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16)); card.setBackground(bg);
        shizukuState = text("正在检查 Shizuku…",14,true); card.addView(shizukuState);
        LinearLayout actions = new LinearLayout(this);
        authorize = new Button(this); authorize.setText("连接 / 授权"); authorize.setOnClickListener(v -> connect());
        Button open = new Button(this); open.setText("打开 Shizuku"); open.setOnClickListener(v -> openShizuku());
        actions.addView(authorize,new LinearLayout.LayoutParams(0,dp(48),1)); actions.addView(open,new LinearLayout.LayoutParams(0,dp(48),1)); card.addView(actions); root.addView(card);
        space(root,8);
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setHint("搜索应用名称或包名");
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int f) {} public void onTextChanged(CharSequence s,int a,int b,int c) { filter(); } public void afterTextChanged(Editable e) {} });
        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        count = text("正在读取应用…",13,false); tools.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        refresh = new Button(this); refresh.setText("刷新"); refresh.setOnClickListener(v -> loadApps()); tools.addView(refresh);
        Button logButton = new Button(this); logButton.setText("日志"); logButton.setOnClickListener(v -> showLogs()); tools.addView(logButton); root.addView(tools);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); progress.setVisibility(View.GONE); root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        result = text("点击应用后确认卸载。卸载会删除用户 0 的应用数据。",13,false); result.setPadding(0,dp(6),0,dp(8)); root.addView(result);
        adapter = new AppAdapter(); list = new ListView(this); list.setAdapter(adapter); list.setDivider(null); list.setClipToPadding(false); list.setPadding(0,0,0,dp(12));
        list.setOnItemClickListener((p,v,position,id) -> confirm(visible.get(position)));
        list.setOnItemLongClickListener((p,v,position,id) -> { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("包名",visible.get(position).pkg)); Toast.makeText(this,"包名已复制",Toast.LENGTH_SHORT).show(); return true; });
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private void space(LinearLayout root, int height) { root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private boolean ownerUser() { return android.os.Process.myUid()/100000 == 0; }
    private void updateConnection() {
        if (destroyed) return;
        boolean alive = Shizuku.pingBinder();
        String message = !ownerUser() ? "请在主用户（用户 0）中运行" : !alive ? "Shizuku 未运行" : binding ? "正在连接卸载服务…" : service != null ? "Shizuku 已连接 · 可卸载" : "Shizuku 已运行 · 请连接授权";
        shizukuState.setText(message); shizukuState.setTextColor(service != null ? GREEN : INK);
        authorize.setEnabled(!busy && !binding && ownerUser());
    }
    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) { startActivity(launch); return; }
        new AlertDialog.Builder(this).setTitle("需要安装 Shizuku")
            .setMessage("请从 Shizuku 官方网站安装，并按引导通过无线调试或电脑启动。手机重启后通常需要重新启动 Shizuku。")
            .setPositiveButton("打开官网",(d,w) -> { try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/"))); } catch (Exception e) { setResult("请访问 https://shizuku.rikka.app",true); } }).setNegativeButton("取消",null).show();
    }
    private void connect() {
        if (busy || binding || !ownerUser()) return;
        try {
            if (!Shizuku.pingBinder()) { setResult("请先打开并启动 Shizuku，再回来连接。",true); return; }
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 13) { setResult("请升级到 Shizuku 13 或更新版本。",true); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) { setResult("请在 Shizuku 的应用管理中允许本工具使用权限。",true); return; }
                Shizuku.requestPermission(1); return;
            }
            if (service != null) { updateConnection(); return; }
            binding = true; updateConnection(); Shizuku.bindUserService(serviceArgs,connection);
            main.postDelayed(() -> { if (!destroyed && binding) { binding=false; setResult("连接服务超时，请检查 Shizuku 并重试。",true); updateConnection(); } },15000);
        } catch (Exception e) { binding=false; setResult("连接失败："+e.getMessage(),true); updateConnection(); }
    }
    private void setBusy(boolean value) { busy=value; refresh.setEnabled(!value); list.setEnabled(!value); progress.setVisibility(value?View.VISIBLE:View.GONE); updateConnection(); }
    private void setResult(String message, boolean error) { result.setText(message); result.setTextColor(error?Color.rgb(176,38,46):INK); addLog(message); }
    private void addLog(String line) {
        logs.append(new java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())).append("  ").append(line).append('\n');
        if (logs.length()>20000) logs.delete(0,logs.length()-16000);
    }
    private void loadApps() {
        if (busy) return;
        if (!ownerUser()) { setResult("本工具只支持用户 0，请在主用户中安装运行。",true); return; }
        setBusy(true); setResult("正在读取应用名称和图标…",false);
        worker.execute(() -> {
            try {
                PackageManager pm = getPackageManager(); ArrayList<AppEntry> loaded = new ArrayList<>();
                for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                    String name; Bitmap icon = null;
                    try { name=pm.getApplicationLabel(info).toString(); } catch (Exception e) { name=info.packageName; }
                    try { Drawable drawable=pm.getApplicationIcon(info); icon=Bitmap.createBitmap(dp(40),dp(40),Bitmap.Config.ARGB_8888); drawable.setBounds(0,0,icon.getWidth(),icon.getHeight()); drawable.draw(new Canvas(icon)); } catch (Exception ignored) {}
                    loaded.add(new AppEntry(info.packageName,name,icon,(info.flags&ApplicationInfo.FLAG_SYSTEM)!=0));
                }
                Collator order = Collator.getInstance(); loaded.sort((a,b) -> order.compare(a.name,b.name));
                main.post(() -> { if (destroyed) return; apps.clear(); apps.addAll(loaded); filter(); setBusy(false); setResult("已读取 "+apps.size()+" 个应用。点击应用卸载，长按复制包名。",false); });
            } catch (Exception e) { main.post(() -> { if (!destroyed) { setBusy(false); setResult("读取失败："+e.getMessage(),true); } }); }
        });
    }
    private void filter() {
        visible.clear(); String query=search==null?"":search.getText().toString();
        for (AppEntry app: apps) if (PackageRules.matches(app.name,app.pkg,query)) visible.add(app);
        if (adapter!=null) adapter.notifyDataSetChanged();
        if (count!=null) count.setText(visible.isEmpty() && !apps.isEmpty()?"没有匹配的应用":visible.size()+" / "+apps.size()+" 个应用");
    }
    private void confirm(AppEntry app) {
        if (busy) return;
        if (!PackageRules.valid(app.pkg) || app.pkg.equals(getPackageName()) || app.pkg.equals("moe.shizuku.privileged.api")) { setResult("此应用不能通过本工具卸载。",true); return; }
        if (service==null || !Shizuku.pingBinder()) { setResult("请先连接并授权 Shizuku。",true); return; }
        new AlertDialog.Builder(this).setTitle("卸载“"+app.name+"”？")
            .setMessage(app.pkg+"\n\n将卸载用户 0 下的此应用，并删除其应用数据。"+(app.system?"\n这是系统应用，卸载可能影响系统功能；系统分区中的本体可能仍保留。":"")+"\n\n此操作无法由本工具撤销。")
            .setNegativeButton("取消",null).setPositiveButton("确认卸载",(d,w) -> uninstall(app)).show();
    }
    private void uninstall(AppEntry app) {
        if (busy || service==null) return;
        IUninstallService target=service;
        setBusy(true); setResult("正在卸载并核对："+app.name+"\n"+app.pkg,false);
        worker.execute(() -> {
            String outcome;
            try { outcome=target.uninstall(app.pkg); } catch (Exception e) { outcome="ERROR\n服务已断开或调用失败，请刷新核对应用状态："+e.getMessage(); }
            final String response=outcome;
            main.post(() -> {
                if (destroyed) return;
                boolean ok=response.startsWith("SUCCESS\n") || response.startsWith("ALREADY_ABSENT\n");
                if (ok) { apps.remove(app); filter(); }
                setBusy(false); setResult(response.contains("\n")?response.substring(response.indexOf('\n')+1):response,!ok);
            });
        });
    }
    private void showLogs() {
        TextView body=text(logs.length()==0?"暂无日志":logs.toString(),12,false); body.setTextIsSelectable(true); body.setPadding(dp(16),dp(8),dp(16),dp(8));
        ScrollView scroll=new ScrollView(this); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("执行日志").setView(scroll).setPositiveButton("关闭",null)
            .setNeutralButton("复制",(d,w) -> { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("卸载日志",logs.toString())); Toast.makeText(this,"日志已复制",Toast.LENGTH_SHORT).show(); }).show();
    }
    @Override protected void onResume() { super.onResume(); if (shizukuState!=null) updateConnection(); }
    @Override protected void onDestroy() {
        destroyed=true;
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(died); Shizuku.removeRequestPermissionResultListener(permission);
        // Detach the callback; non-daemon Shizuku stops the service when this app process exits.
        try { Shizuku.unbindUserService(serviceArgs,connection,false); } catch (Exception ignored) {}
        worker.shutdown(); main.removeCallbacksAndMessages(null); super.onDestroy();
    }
    private static final class AppEntry {
        final String pkg,name; final Bitmap icon; final boolean system;
        AppEntry(String pkg,String name,Bitmap icon,boolean system) { this.pkg=pkg;this.name=name;this.icon=icon;this.system=system; }
    }
    private final class AppAdapter extends BaseAdapter {
        public int getCount(){return visible.size();} public Object getItem(int p){return visible.get(p);} public long getItemId(int p){return p;}
        public View getView(int position,View recycled,android.view.ViewGroup parent) {
            LinearLayout row; ImageView icon; TextView name,pkg;
            if (recycled==null) {
                row=new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10),dp(12),dp(10),dp(12));
                icon=new ImageView(MainActivity.this); row.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
                LinearLayout words=column(); words.setPadding(dp(12),0,dp(6),0); name=text("",15,true); pkg=text("",11,false); pkg.setTextColor(Color.rgb(103,115,125));
                words.addView(name); words.addView(pkg); row.addView(words,new LinearLayout.LayoutParams(0,-2,1)); row.setTag(new View[]{icon,name,pkg});
            } else row=(LinearLayout)recycled;
            View[] views=(View[])row.getTag(); icon=(ImageView)views[0]; name=(TextView)views[1]; pkg=(TextView)views[2];
            AppEntry entry=visible.get(position); if(entry.icon!=null)icon.setImageBitmap(entry.icon);else icon.setImageResource(android.R.drawable.sym_def_app_icon);
            name.setText(entry.name); pkg.setText(entry.pkg+(entry.system?"  ·  系统应用":""));
            row.setContentDescription(entry.name+"，"+entry.pkg+(entry.system?"，系统应用":"")); return row;
        }
    }
}
