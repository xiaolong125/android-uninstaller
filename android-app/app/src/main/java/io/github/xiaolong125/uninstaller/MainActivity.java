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

public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
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
    private int GREEN, INK, muted, errorColor;
    private int appFilter;
    private TextView connectionHint;
    private View empty;
    private com.google.android.material.card.MaterialCardView connectionCard;
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
    private void buildUI() {
        GREEN = getColor(R.color.primary); INK = getColor(R.color.on_surface);
        muted = getColor(R.color.on_surface_variant); errorColor = getColor(R.color.error);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        View content = findViewById(R.id.content);
        ScrollView header = findViewById(R.id.header_scroll);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                | androidx.core.view.WindowInsetsCompat.Type.displayCutout() | androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat.getInsetsController(getWindow(), root);
        bars.setAppearanceLightStatusBars(getResources().getBoolean(R.bool.light_system_bars));
        bars.setAppearanceLightNavigationBars(getResources().getBoolean(R.bool.light_system_bars));
        Runnable resize = () -> {
            int width = Math.min(dp(760), root.getWidth() - root.getPaddingLeft() - root.getPaddingRight());
            if (width > 0 && content.getLayoutParams().width != width) {
                ViewGroup.LayoutParams params = content.getLayoutParams(); params.width = width; content.setLayoutParams(params);
            }
            LinearLayout actions = findViewById(R.id.connection_actions);
            boolean stacked = width < dp(360) || getResources().getConfiguration().fontScale > 1.2f;
            int orientation = stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
            if (actions.getOrientation() != orientation) {
                actions.setOrientation(orientation);
                View secondary = findViewById(R.id.open_shizuku);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)secondary.getLayoutParams();
                params.setMarginStart(stacked ? 0 : dp(8)); params.topMargin = stacked ? dp(4) : 0;
                secondary.setLayoutParams(params);
            }
            int available = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
            int natural = header.getChildAt(0).getMeasuredHeight();
            int height = Math.min(natural, Math.max(dp(96), available - dp(243)));
            if (height > 0 && header.getLayoutParams().height != height) {
                ViewGroup.LayoutParams params = header.getLayoutParams(); params.height = height; header.setLayoutParams(params);
            }
        };
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> resize.run());
        header.getChildAt(0).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> resize.run());
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        MenuItem logItem = toolbar.getMenu().add("执行日志").setIcon(R.drawable.ic_log);
        logItem.setIconTintList(android.content.res.ColorStateList.valueOf(INK));
        logItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> { showLogs(); return true; });
        shizukuState = findViewById(R.id.connection_state); connectionHint = findViewById(R.id.connection_hint);
        connectionCard = findViewById(R.id.connection_card);
        authorize = findViewById(R.id.authorize); authorize.setOnClickListener(v -> connect());
        findViewById(R.id.open_shizuku).setOnClickListener(v -> openShizuku());
        search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int f) {} public void onTextChanged(CharSequence s,int a,int b,int c) { filter(); } public void afterTextChanged(Editable e) {} });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
            search.clearFocus(); return true;
        });
        com.google.android.material.chip.ChipGroup filters = findViewById(R.id.filters);
        filters.setOnCheckedStateChangeListener((group, checked) -> {
            appFilter = checked.contains(R.id.filter_system) ? 2 : checked.contains(R.id.filter_user) ? 1 : 0; filter();
        });
        count = findViewById(R.id.count); refresh = findViewById(R.id.refresh); refresh.setOnClickListener(v -> loadApps());
        progress = findViewById(R.id.progress); result = findViewById(R.id.result); empty = findViewById(R.id.empty);
        list = findViewById(R.id.app_list); adapter = new AppAdapter(); list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,position,id) -> confirm(visible.get(position)));
        list.setOnItemLongClickListener((p,v,position,id) -> { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("包名",visible.get(position).pkg)); Toast.makeText(this,"包名已复制",Toast.LENGTH_SHORT).show(); return true; });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }
    private boolean ownerUser() { return android.os.Process.myUid()/100000 == 0; }
    private void updateConnection() {
        if (destroyed) return;
        boolean alive = Shizuku.pingBinder();
        String message = !ownerUser() ? "请在主用户（用户 0）中运行" : !alive ? "Shizuku 未运行" : binding ? "正在连接卸载服务…" : service != null ? "Shizuku 已连接 · 可卸载" : "Shizuku 已运行 · 请连接授权";
        shizukuState.setText(message); shizukuState.setTextColor(service != null ? GREEN : INK);
        connectionHint.setText(service != null ? "已授权 · 仅处理主用户下的应用" : "浏览无需授权，卸载前请连接 Shizuku");
        authorize.setText(service != null ? "已连接" : binding ? "连接中…" : "连接 / 授权");
        connectionCard.setCardBackgroundColor(getColor(service != null ? R.color.primary_container : R.color.secondary_container));
        authorize.setEnabled(!busy && !binding && ownerUser() && service == null);
    }
    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) { startActivity(launch); return; }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("需要安装 Shizuku")
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
    private void setBusy(boolean value) { busy=value; refresh.setEnabled(!value); list.setEnabled(!value); progress.setVisibility(value?View.VISIBLE:View.INVISIBLE); if (empty != null) empty.setVisibility(!value && visible.isEmpty() ? View.VISIBLE : View.GONE); updateConnection(); }
    private void setResult(String message, boolean error) { result.setText(message); result.setTextColor(error?errorColor:muted); addLog(message); }
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
        for (AppEntry app: apps) if ((appFilter == 0 || (appFilter == 2) == app.system) && PackageRules.matches(app.name,app.pkg,query)) visible.add(app);
        if (adapter!=null) adapter.notifyDataSetChanged();
        if (count!=null) count.setText("应用列表 · " + visible.size() + " / " + apps.size());
        if (empty!=null) empty.setVisibility(!busy && visible.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private void confirm(AppEntry app) {
        if (busy) return;
        if (!PackageRules.valid(app.pkg) || app.pkg.equals(getPackageName()) || app.pkg.equals("moe.shizuku.privileged.api")) { setResult("此应用不能通过本工具卸载。",true); return; }
        if (service==null || !Shizuku.pingBinder()) { setResult("请先连接并授权 Shizuku。",true); return; }
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("卸载“"+app.name+"”？")
            .setMessage(app.pkg+"\n\n将卸载用户 0 下的此应用，并删除其应用数据。"+(app.system?"\n这是系统应用，卸载可能影响系统功能；系统分区中的本体可能仍保留。":"")+"\n\n此操作无法由本工具撤销。")
            .setNegativeButton("取消",null).setPositiveButton("确认卸载",(d,w) -> uninstall(app)).show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(errorColor);
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("执行日志").setView(scroll).setPositiveButton("关闭",null)
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
        public View getView(int position, View recycled, android.view.ViewGroup parent) {
            View row = recycled == null ? getLayoutInflater().inflate(R.layout.item_app, parent, false) : recycled;
            ImageView icon = row.findViewById(R.id.app_icon);
            TextView name = row.findViewById(R.id.app_name), pkg = row.findViewById(R.id.app_package), type = row.findViewById(R.id.app_type);
            ImageView action = row.findViewById(R.id.app_action);
            AppEntry entry = visible.get(position);
            if(entry.icon!=null) icon.setImageBitmap(entry.icon); else icon.setImageResource(android.R.drawable.sym_def_app_icon);
            boolean protectedApp = entry.pkg.equals(getPackageName()) || entry.pkg.equals("moe.shizuku.privileged.api") || !PackageRules.valid(entry.pkg);
            name.setText(entry.name); pkg.setText(entry.pkg);
            type.setText(protectedApp ? "受保护 · 不可卸载" : entry.system ? "系统应用" : "用户应用");
            action.setImageResource(protectedApp ? R.drawable.ic_shield : R.drawable.ic_delete);
            row.setContentDescription(entry.name + "，" + entry.pkg + "，" + type.getText() + (protectedApp ? "" : "，点按卸载") + "，长按复制包名");
            return row;
        }
    }
}
