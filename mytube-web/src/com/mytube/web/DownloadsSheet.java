package com.mytube.web;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * ইন-অ্যাপ ডাউনলোড-স্ক্রিন (SnapTube-ধাঁচ): প্রতিটি জবের লাইভ প্রগ্রেস
 * (% · গতি · ধাপ), লাইন, সাম্প্রতিক-শেষ — প্রতি ০.৫ সেকেন্ডে রিফ্রেশ।
 */
public final class DownloadsSheet {

    private DownloadsSheet() {
    }

    public static void show(final Activity a) {
        try {
            final Dialog dg = new Dialog(a, android.R.style.Theme_Material_NoActionBar);
            ScrollView sv = new ScrollView(a);
            final LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 16));
            box.setBackgroundColor(0xFF0F0F0F);
            sv.setBackgroundColor(0xFF0F0F0F);
            sv.addView(box);
            sv.setVerticalScrollBarEnabled(false);
            TextView title = new TextView(a);
            title.setText("Downloads");
            title.setTextSize(24);
            title.setTypeface(null, 1);
            title.setTextColor(0xFFF2F2F2);
            box.addView(title);
            final LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            box.addView(list);
            final Handler h = new Handler(Looper.getMainLooper());
            final Runnable tick = new Runnable() {
                @Override
                public void run() {
                    if (!dg.isShowing()) {
                        return;
                    }
                    rebuild(a, list);
                    h.postDelayed(this, 500);
                }
            };
            dg.setContentView(sv);
            dg.setCanceledOnTouchOutside(true);
            Window win = dg.getWindow();
            if (win != null) {
                win.setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(0xFF0F0F0F));
                win.setLayout(-1, -1);
            }
            dg.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    h.removeCallbacks(tick);
                }
            });
            dg.show();
            h.post(tick);
        } catch (Throwable t) {
        }
    }

    private static final boolean[] secActive = {true};
    private static final boolean[] secDone = {true};
    private static final boolean[] secWL = {false};
    private static final boolean[] secPL = {false};
    private static String openPl = null;
    private static final boolean[] secHist = {false};

    private static void rebuild(Context c, LinearLayout list) {
        try {
            list.removeAllViews();
            java.util.ArrayList<DownloadService.Snap> snaps = DownloadService.snapshot();
            java.util.ArrayList<Object[]> dls = MainActivity.queryDownloads(c);
            org.json.JSONArray wl = Library.wl(c);
            java.util.ArrayList<String> pls = Library.playlists(c);
            java.util.ArrayList<Object[]> hist = DownloadService.histList(c);
            if (snaps.isEmpty() && dls.isEmpty() && wl.length() == 0
                    && pls.isEmpty() && hist.isEmpty()) {
                TextView e = new TextView(c);
                e.setText("No downloads yet");
                e.setTextColor(0xFF6F6F75);
                e.setTextSize(13);
                e.setPadding(0, dp(c, 12), 0, 0);
                list.addView(e);
                return;
            }
            // ---- Active: চলমান + সাম্প্রতিক জব (Pause/Resume/Retry/Cancel) ----
            if (!snaps.isEmpty()) {
                list.addView(section(c, list, "Active", snaps.size(), secActive[0], secActive));
                if (secActive[0]) {
                    for (int i2 = 0; i2 < snaps.size(); i2++) {
                        list.addView(row(c, snaps.get(i2)));
                        if (i2 < snaps.size() - 1) {
                            View hair = new View(c);
                            hair.setBackgroundColor(0xFF232326);
                            hair.setLayoutParams(new LinearLayout.LayoutParams(-1,
                                    Math.max(1, dp(c, 1))));
                            list.addView(hair);
                        }
                    }
                }
            }
            // ---- Completed: নামানো ফাইল ----
            if (!dls.isEmpty()) {
                list.addView(section(c, list, "Completed", dls.size(), secDone[0], secDone));
                if (secDone[0]) {
                    int lim = Math.min(dls.size(), 30);
                    for (int i2 = 0; i2 < lim; i2++) {
                        list.addView(fileRow(c, dls.get(i2)));
                        if (i2 < lim - 1) {
                            View hair = new View(c);
                            hair.setBackgroundColor(0xFF1C1C1F);
                            hair.setLayoutParams(new LinearLayout.LayoutParams(-1,
                                    Math.max(1, dp(c, 1))));
                            list.addView(hair);
                        }
                    }
                }
            }
            // ---- Playlists: নাম-দেওয়া তালিকা (ট্যাপে ভেতরের ভিডিও) ----
            if (!pls.isEmpty()) {
                list.addView(section(c, list, "Playlists", pls.size(), secPL[0], secPL));
                if (secPL[0]) {
                    for (int i2 = 0; i2 < pls.size(); i2++) {
                        String pn = pls.get(i2);
                        list.addView(plRow(c, list, pn));
                        if (pn.equals(openPl)) {
                            org.json.JSONArray items = Library.pl(c, pn);
                            for (int k2 = 0; k2 < items.length(); k2++) {
                                org.json.JSONObject o = items.optJSONObject(k2);
                                if (o == null) {
                                    continue;
                                }
                                list.addView(plItemRow(c, o.optString("id", ""),
                                        o.optString("t", "")));
                            }
                        }
                    }
                }
            }
            // ---- Watch Later: সেভ-করা ভিডিও ----
            if (wl.length() > 0) {
                list.addView(section(c, list, "Watch Later", wl.length(), secWL[0], secWL));
                if (secWL[0]) {
                    for (int i2 = 0; i2 < wl.length(); i2++) {
                        org.json.JSONObject o = wl.optJSONObject(i2);
                        if (o == null) {
                            continue;
                        }
                        list.addView(wlRow(c, o.optString("id", ""), o.optString("t", "")));
                    }
                }
            }
            // ---- History: ডাউনলোড-ইতিহাস ----
            if (!hist.isEmpty()) {
                list.addView(section(c, list, "History", hist.size(), secHist[0], secHist));
                if (secHist[0]) {
                    int lim = Math.min(hist.size(), 30);
                    for (int i2 = 0; i2 < lim; i2++) {
                        list.addView(histRow(c, hist.get(i2)));
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    /** সেকশন-হেডার: ক্লিকে খোলে/বন্ধ হয় (গণনা + তীর-চিহ্নসহ) */
    private static View section(final Context c, final LinearLayout list, String title,
            int count, boolean expanded, final boolean[] flag) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(android.view.Gravity.CENTER_VERTICAL);
        h.setPadding(0, dp(c, 14), 0, dp(c, 6));
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(0xFFF2F2F2);
        t.setTextSize(15);
        t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        h.addView(t, new LinearLayout.LayoutParams(-2, -2, 1f));
        TextView n = new TextView(c);
        n.setText(String.valueOf(count));
        n.setTextColor(0xFF9A9A9A);
        n.setTextSize(12);
        h.addView(n);
        SectionArrow ar = new SectionArrow(c);
        ar.setExpanded(expanded);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(c, 14), dp(c, 14));
        alp.leftMargin = dp(c, 8);
        ar.setLayoutParams(alp);
        h.addView(ar);
        h.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flag[0] = !flag[0];
                rebuild(c, list);
            }
        });
        return h;
    }

    /** সেকশন-তীর: খোলা = নিচমুখী, বন্ধ = ডানমুখী */
    private static final class SectionArrow extends View {
        private final android.graphics.Paint p =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private boolean exp = true;

        SectionArrow(Context c) {
            super(c);
            p.setColor(0xFF9A9A9A);
            p.setStyle(android.graphics.Paint.Style.FILL);
        }

        void setExpanded(boolean e) {
            exp = e;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            android.graphics.Path pa = new android.graphics.Path();
            if (exp) {
                pa.moveTo(w * 0.15f, h * 0.35f);
                pa.lineTo(w * 0.85f, h * 0.35f);
                pa.lineTo(w * 0.5f, h * 0.8f);
            } else {
                pa.moveTo(w * 0.35f, h * 0.15f);
                pa.lineTo(w * 0.35f, h * 0.85f);
                pa.lineTo(w * 0.8f, h * 0.5f);
            }
            pa.close();
            cv.drawPath(pa, p);
        }
    }

    /** Watch Later-সারি: ট্যাপ = খোলা, চেপে-ধরা = সরানো */
    private static View wlRow(final Context c, final String id, final String title) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(c, 10), 0, dp(c, 10));
        TextView t1 = new TextView(c);
        t1.setText(title != null && title.length() > 0 ? title : id);
        t1.setTextColor(0xFFF2F2F2);
        t1.setTextSize(14);
        t1.setMaxLines(2);
        t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t1);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (c instanceof MainActivity) {
                        ((MainActivity) c).openVideoById(id, title);
                    }
                } catch (Throwable t) {
                }
            }
        });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Library.removeWl(c, id);
                android.widget.Toast.makeText(c, "Removed from Watch Later",
                        android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        return r;
    }

    /** প্লেলিস্ট-সারি: ট্যাপে ভেতরের ভিডিওগুলো খোলে/বন্ধ হয় */
    private static View plRow(final Context c, final LinearLayout list, final String name) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(c, 10), 0, dp(c, 4));
        TextView t1 = new TextView(c);
        t1.setText(name);
        t1.setTextColor(0xFFF2F2F2);
        t1.setTextSize(14);
        t1.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        r.addView(t1, new LinearLayout.LayoutParams(-2, -2, 1f));
        TextView n = new TextView(c);
        n.setText(String.valueOf(Library.pl(c, name).length()) + " videos");
        n.setTextColor(0xFF9A9A9A);
        n.setTextSize(12);
        r.addView(n);
        SectionArrow ar = new SectionArrow(c);
        ar.setExpanded(name.equals(openPl));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(c, 12), dp(c, 12));
        alp.leftMargin = dp(c, 6);
        ar.setLayoutParams(alp);
        r.addView(ar);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPl = openPl != null && openPl.equals(name) ? null : name;
                rebuild(c, list);
            }
        });
        return r;
    }

    /** প্লেলিস্টের ভেতরের ভিডিও-সারি: ট্যাপ = খোলা */
    private static View plItemRow(final Context c, final String id, final String title) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(c, 16), dp(c, 8), 0, dp(c, 8));
        TextView t1 = new TextView(c);
        t1.setText(title != null && title.length() > 0 ? title : id);
        t1.setTextColor(0xFFDADADA);
        t1.setTextSize(13);
        t1.setMaxLines(2);
        t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t1);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (c instanceof MainActivity) {
                        ((MainActivity) c).openVideoById(id, title);
                    }
                } catch (Throwable t) {
                }
            }
        });
        return r;
    }

    /** হিস্ট্রি-সারি: শুধু তথ্য (শিরোনাম [লেবেল] + ফল + তারিখ) */
    private static View histRow(Context c, Object[] h) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(c, 9), 0, dp(c, 9));
        TextView t1 = new TextView(c);
        String lb = (String) h[1];
        t1.setText((String) h[0] + (lb.length() > 0 ? "  [" + lb + "]" : ""));
        t1.setTextColor(0xFFDADADA);
        t1.setTextSize(13);
        t1.setMaxLines(1);
        t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t1);
        TextView t2 = new TextView(c);
        String st = (String) h[2];
        t2.setText(st + "  \u00B7  " + dateStr(((Long) h[3]).longValue()));
        t2.setTextColor("Failed".equals(st) ? 0xFFE8635A : 0xFF8E8E93);
        t2.setTextSize(12);
        r.addView(t2);
        return r;
    }

    private static View fileRow(final Context c, final Object[] d) {
        final String name = (String) d[0];
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(c, 11), 0, dp(c, 11));
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(c);
        t1.setText(name);
        t1.setTextColor(0xFFF2F2F2);
        t1.setTextSize(14);
        t1.setMaxLines(2);
        t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(t1);
        TextView t2 = new TextView(c);
        t2.setText(sizeStr((Long) d[1]) + "  \u00B7  " + dateStr((Long) d[2]));
        t2.setTextColor(0xFF9A9A9A);
        t2.setTextSize(12);
        txt.addView(t2);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        Button del = new Button(c);
        del.setText("Delete");
        del.setTextSize(11);
        del.setTextColor(0xFFE8635A);
        del.setBackgroundColor(0xFF242424);
        del.setPadding(dp(c, 10), dp(c, 2), dp(c, 10), dp(c, 2));
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    new android.app.AlertDialog.Builder(c)
                            .setTitle("Delete file")
                            .setMessage(name)
                            .setPositiveButton("Delete",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d2, int w2) {
                                            boolean ok = MainActivity.deleteDownloaded(
                                                    c, (android.net.Uri) d[3], name);
                                            android.widget.Toast.makeText(c, ok
                                                    ? "Deleted" : "Could not delete",
                                                    android.widget.Toast.LENGTH_SHORT).show();
                                        }
                                    })
                            .setNegativeButton("Cancel", null)
                            .show();
                } catch (Throwable t) {
                }
            }
        });
        r.addView(del);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.openDownloaded(c, (android.net.Uri) d[3], name);
            }
        });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    android.content.Intent sh = new android.content.Intent(
                            android.content.Intent.ACTION_SEND);
                    String mime = name.endsWith(".mp3") ? "audio/mpeg"
                            : name.endsWith(".srt") ? "text/plain"
                            : name.endsWith(".m4a") ? "audio/mp4" : "video/mp4";
                    sh.setType(mime);
                    sh.putExtra(android.content.Intent.EXTRA_STREAM, (android.net.Uri) d[3]);
                    sh.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    c.startActivity(android.content.Intent.createChooser(sh, name));
                } catch (Throwable t) {
                }
                return true;
            }
        });
        return r;
    }

    private static String sizeStr(long bytes) {
        if (bytes >= 1048576L) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
        }
        if (bytes >= 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return bytes + " B";
    }

    private static String dateStr(long millis) {
        try {
            return new java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                    .format(new java.util.Date(millis));
        } catch (Throwable t) {
            return "";
        }
    }

    private static View row(final Context c, final DownloadService.Snap s) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(c, 12), 0, dp(c, 12));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t = new TextView(c);
        String name = (s.title != null ? s.title : "Download")
                + (s.label != null && s.label.length() > 0 ? "  [" + s.label + "]" : "");
        t.setText(name);
        t.setTextColor(0xFFF2F2F2);
        t.setTextSize(14);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!s.done) {
            if (s.phase != 3) {
                Button pp = new Button(c);
                pp.setText(s.paused ? "Resume" : "Pause");
                pp.setTextSize(11);
                pp.setTextColor(0xFFF2F2F2);
                pp.setBackgroundColor(0xFF242424);
                pp.setPadding(dp(c, 10), dp(c, 2), dp(c, 10), dp(c, 2));
                pp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            c.startService(new Intent(c, DownloadService.class)
                                    .setAction(s.paused ? DownloadService.ACT_RESUME
                                            : DownloadService.ACT_PAUSE)
                                    .putExtra(DownloadService.EXTRA_JID, s.slot));
                        } catch (Throwable t2) {
                        }
                    }
                });
                top.addView(pp);
            }
            Button cancel = new Button(c);
            cancel.setText("Cancel");
            cancel.setTextSize(11);
            cancel.setTextColor(0xFF9A9A9A);
            cancel.setBackgroundColor(0xFF242424);
            cancel.setPadding(dp(c, 10), dp(c, 2), dp(c, 10), dp(c, 2));
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        c.startService(new Intent(c, DownloadService.class)
                                .setAction(DownloadService.ACT_CANCEL)
                                .putExtra(DownloadService.EXTRA_JID, s.slot));
                    } catch (Throwable t2) {
                    }
                }
            });
            top.addView(cancel);
        } else if (s.failed) {
            Button rt = new Button(c);
            rt.setText("Retry");
            rt.setTextSize(11);
            rt.setTextColor(0xFF34D399);
            rt.setBackgroundColor(0xFF242424);
            rt.setPadding(dp(c, 10), dp(c, 2), dp(c, 10), dp(c, 2));
            rt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent ri = new Intent(c, DownloadService.class)
                                .setAction(DownloadService.ACT_START)
                                .putExtra(DownloadService.EXTRA_TITLE, s.title)
                                .putExtra(DownloadService.EXTRA_LABEL, s.label);
                        if (s.v != null) {
                            ri.putExtra(DownloadService.EXTRA_V, s.v);
                        }
                        if (s.a != null) {
                            ri.putExtra(DownloadService.EXTRA_A, s.a);
                        }
                        if (s.direct != null) {
                            ri.putExtra(DownloadService.EXTRA_DIRECT, s.direct);
                        }
                        if (s.srt != null) {
                            ri.putExtra(DownloadService.EXTRA_SRT, s.srt);
                        }
                        if (s.mp3 > 0) {
                            ri.putExtra(DownloadService.EXTRA_MP3, s.mp3);
                        }
                        c.startService(ri);
                    } catch (Throwable t2) {
                    }
                }
            });
            top.addView(rt);
        }
        r.addView(top);

        TextView status = new TextView(c);
        status.setTextSize(12);
        int tint = 0xFF34D399;
        if (s.done) {
            status.setText(s.msg != null ? s.msg : "");
            status.setTextColor(s.failed ? 0xFFE8635A : 0xFF34D399);
        } else {
            status.setTextColor(0xFF9A9A9A);
            status.setText(statusText(s));
        }
        status.setMaxLines(2);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(status);

        ProgressBar pb = new ProgressBar(c, null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        try {
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(
                    s.done && s.failed ? 0xFFE8635A : 0xFF34D399));
            pb.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF242427));
        } catch (Throwable t2) {
        }
        if (s.done) {
            pb.setProgress(s.failed ? 100 : 100);
        } else if (s.phase == 3) {
            pb.setProgress(0);
        } else if (s.phase == 1) {
            int pct = s.muxTotal > 0 ? (int) Math.min(99,
                    95 + 5 * s.muxWritten / s.muxTotal) : 96;
            pb.setProgress(pct);
        } else if (s.phase == 2) {
            pb.setProgress(97);
            pb.setIndeterminate(true);
        } else {
            pb.setProgress(s.total > 0
                    ? (int) Math.min(95, 95 * s.got / s.total) : 0);
        }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(c, 6));
        plp.topMargin = dp(c, 8);
        pb.setLayoutParams(plp);
        r.addView(pb);
        return r;
    }

    private static String statusText(DownloadService.Snap s) {
        if (s.paused && !s.done && s.phase == 0) {
            return "Paused";
        }
        if (s.phase == 3) {
            return "Queued";
        }
        if (s.phase == 1) {
            return "Muxing video and audio";
        }
        if (s.phase == 2) {
            return "Converting to MP3";
        }
        long got = s.got;
        double sec = Math.max(0.001, (System.currentTimeMillis() - s.t0) / 1000.0);
        double mbs = s.speed > 0 ? s.speed : got / 1048576.0 / sec;
        if (s.total > 0) {
            int pct = (int) Math.min(95, 95 * got / s.total);
            return String.format(java.util.Locale.US, "%d%%  ·  %.1f MB/s  ·  %.1f of %.1f MB",
                    pct, mbs, got / 1048576.0, s.total / 1048576.0);
        }
        return String.format(java.util.Locale.US, "%.1f MB  ·  %.1f MB/s",
                got / 1048576.0, mbs);
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
