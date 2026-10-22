package com.mytube.web;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * ইউটিউবের বাইরের প্ল্যাটফর্ম থেকে সরাসরি ভিডিও-লিংক বের করা — yt-dlp যেভাবে
 * করে, ঠিক সেই পদ্ধতিতেই (পেজ/এন্ডপয়েন্টের ভেতরের JSON পড়ে), কিন্তু নেটিভ
 * জাভায়।
 *
 * <p>কেন yt-dlp নিজে নয়: yt-dlp একটি পাইথন প্রোগ্রাম। অ্যাপে চালাতে গেলে
 * arm64-র জন্য CPython রানটাইম (প্রায় ২০ MB) এবং Gradle/AAR বিল্ড লাগে —
 * এই অফলাইন javac/d8 পাইপলাইনে দুটোরই সুযোগ নেই। তাই এক্সট্র্যাকশনের
 * *পদ্ধতিটি* নেটিভভাবে করা হয়েছে; ফলাফল একই ধরনের।
 *
 * <p>সব ফাংশনই বিশুদ্ধ (pure) — নেটওয়ার্ক ছাড়া টেস্ট করা যায়।
 */
public final class Extractors {

    private Extractors() {
    }

    /** একটি পাওয়া স্ট্রিম। */
    public static final class Hit {
        public final String url;
        public final String ext;
        public final boolean audio;
        public final String label; // "HD", "720p", "Audio" ...

        public Hit(String u, String e, boolean a, String l) {
            url = u;
            ext = e;
            audio = a;
            label = l;
        }
    }

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** এই হোস্টগুলো আমরা নিজে সামলাতে পারি? */
    public static boolean canHandle(String url) {
        String h = host(url);
        if (h.length() == 0) {
            return false;
        }
        return h.endsWith("tiktok.com")
                || h.equals("fb.watch") || h.endsWith("facebook.com")
                || h.endsWith("fb.com") || h.endsWith("instagram.com")
                || h.endsWith("instagr.am");
    }

    /** হোস্ট-অনুযায়ী এক্সট্র্যাক্টর। ব্যর্থ হলে খালি তালিকা। */
    public static ArrayList<Hit> extract(String url) {
        ArrayList<Hit> out = new ArrayList<Hit>();
        try {
            String h = host(url);
            String html;
            if (h.endsWith("tiktok.com")) {
                html = get(resolveRedirects(url));
                if (html != null) {
                    out = parseTikTok(html);
                }
                if (out.isEmpty() && html != null) {
                    out = parseTikTokRehydration(html);
                }
            } else if (h.equals("fb.watch") || h.endsWith("facebook.com")
                    || h.endsWith("fb.com")) {
                html = get(url);
                if (html != null) {
                    out = parseFacebook(html);
                }
            } else if (h.endsWith("instagram.com") || h.endsWith("instagr.am")) {
                html = get(url);
                if (html != null) {
                    out = parseInstagram(html);
                }
                if (out.isEmpty()) {
                    html = get(instagramApiUrl(url));
                    if (html != null) {
                        out = parseInstagram(html);
                    }
                }
            }
        } catch (Throwable t) {
            StreamStore.note("extract err " + t.getClass().getSimpleName());
        }
        return out;
    }

    // ==================== বিশুদ্ধ পার্সার (টেস্টযোগ্য) ====================

    /**
     * TikTok: নতুন পেজে `__UNIVERSAL_DATA_FOR_REHYDRATION__`-এ পূর্ণ JSON
     * থাকে; পুরনো/সরল পেজে সোজা `playAddr`/`downloadAddr`। দুটোই দেখি।
     */
    static ArrayList<Hit> parseTikTok(String html) {
        ArrayList<Hit> out = new ArrayList<Hit>();
        if (html == null) {
            return out;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        // মানের গুণমান অনুসারে: downloadAddr আগে, তারপর playAddr
        addUrl(out, seen, firstString(html, "\"downloadAddr\":", "\"playAddr\":"));
        addUrl(out, seen, firstString(html, "\"playAddr\":", "\"downloadAddr\":"));
        addUrl(out, seen, firstString(html, "\"playAddrHd\":", null));
        // bitrateInfo[].PlayAddr.UrlList[] — একাধিক কোয়ালিটি দেয়।
        // আসল পেজে কী-গুলো মিশ্র হাতের ("Bitrate" / "UrlList"), তাই
        // কেস-নিরপেক্ষভাবে খুঁজি।
        try {
            int i = 0;
            while (true) {
                int urls = indexOfCI(html, "\"UrlList\"", i);
                if (urls < 0) {
                    break;
                }
                long bps = 0;
                int band = lastIndexOfCI(html, "\"Bitrate\"", urls);
                if (band >= 0 && urls - band < 800) {
                    int val = html.indexOf(':', band) + 1;
                    int end = val;
                    while (end < html.length()
                            && (Character.isDigit(html.charAt(end))
                                || html.charAt(end) == ' ')) {
                        end++;
                    }
                    try {
                        bps = Long.parseLong(html.substring(val, end).trim());
                    } catch (Throwable t) {
                    }
                }
                String u = firstArrayString(html, urls);
                if (u != null) {
                    String lab = bps >= 1500000 ? "HD"
                            : bps >= 800000 ? "SD" : "Video";
                    addUrl(out, seen, u, lab);
                }
                i = urls + 9;
            }
        } catch (Throwable t) {
        }
        return out;
    }

    /** ব্যাকআপ: পেজের ভেতরে যেকোনো tiktok/video-*.mp4 ঠিকানা খুঁজি। */
    static ArrayList<Hit> parseTikTokRehydration(String html) {
        ArrayList<Hit> out = new ArrayList<Hit>();
        if (html == null) {
            return out;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        int i = 0;
        while (i < html.length()) {
            int k = html.indexOf("https", i);
            if (k < 0) {
                break;
            }
            int end = k;
            while (end < html.length() && end - k < 1200) {
                char c = html.charAt(end);
                if (c == '"' || c == '\'' || c == '\\' && end + 1 < html.length()
                        && (html.charAt(end + 1) == 'u' || html.charAt(end + 1) == '"')) {
                    break;
                }
                if (c == ' ' || c == '<' || c == '>') {
                    break;
                }
                end++;
            }
            String raw = html.substring(k, end);
            String u = unescape(raw);
            if (u.contains(".mp4") && (u.contains("tiktok") || u.contains("tiktokcdn")
                    || u.contains("/video/"))) {
                addUrl(out, seen, u, null);
            }
            i = k + 5;
        }
        return out;
    }

    /** Facebook: hd_src / sd_src, অথবা ফোনের পেজে playable_url*. */
    static ArrayList<Hit> parseFacebook(String html) {
        ArrayList<Hit> out = new ArrayList<Hit>();
        if (html == null) {
            return out;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        addUrl(out, seen, firstString(html, "\"browser_native_hd_url\":", null), "HD");
        addUrl(out, seen, firstString(html, "\"playable_url_quality_hd\":", null), "HD");
        addUrl(out, seen, firstString(html, "\"hd_src\":", null), "HD");
        addUrl(out, seen, firstString(html, "\"browser_native_sd_url\":", null), "SD");
        addUrl(out, seen, firstString(html, "\"playable_url\":", null), "SD");
        addUrl(out, seen, firstString(html, "\"sd_src\":", null), "SD");
        return out;
    }

    /** Instagram: video_versions[] (এন্ডপয়েন্ট বা পেজের ভেতরের JSON)। */
    static ArrayList<Hit> parseInstagram(String body) {
        ArrayList<Hit> out = new ArrayList<Hit>();
        if (body == null) {
            return out;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        addUrl(out, seen, firstString(body, "\"video_url\":", null), "Video");
        try {
            int k = body.indexOf("\"video_versions\"");
            if (k >= 0) {
                int i = k;
                int found = 0;
                while (found < 4) {
                    int u = body.indexOf("\"url\":", i);
                    if (u < 0 || u - k > 4000) {
                        break;
                    }
                    String s = strValue(body, u + 6);
                    addUrl(out, seen, s, found == 0 ? "HD" : "SD");
                    i = u + 6;
                    found++;
                }
            }
        } catch (Throwable t) {
        }
        return out;
    }

    private static int indexOfCI(String body, String key, int from) {
        try {
            String l = body.toLowerCase();
            return l.indexOf(key.toLowerCase(), from);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int lastIndexOfCI(String body, String key, int before) {
        try {
            String l = body.toLowerCase();
            int at = Math.min(before + key.length(), l.length());
            return l.lastIndexOf(key.toLowerCase(), at);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** `"key":"value"` থেকে value (JSON-উদ্ধৃতি/এস্কেপ সামলে)। */
    static String firstString(String body, String key, String altKey) {
        String v = strAfter(body, key);
        if (v == null && altKey != null && altKey.length() > 0) {
            v = strAfter(body, altKey);
        }
        return v;
    }

    private static String strAfter(String body, String key) {
        int k = body.indexOf(key);
        if (k < 0) {
            return null;
        }
        return strValue(body, k + key.length());
    }

    /** key-এর পরে প্রথম JSON স্ট্রিং-মানটি পড়ে (খালি হলে null)। */
    private static String strValue(String body, int from) {
        int i = from;
        while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == ':'
                || body.charAt(i) == '\t')) {
            i++;
        }
        if (i >= body.length() || body.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                sb.append(c).append(body.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
            i++;
        }
        String v = unescape(sb.toString());
        return v.length() == 0 ? null : v;
    }

    private static String firstArrayString(String body, int fromKey) {
        int i = body.indexOf('[', fromKey);
        if (i < 0) {
            return null;
        }
        return strValue(body, i + 1);
    }

    /** JSON/JS এস্কেপ খোলা: \\/ \u002F \u0026 \/ ইত্যাদি। */
    static String unescape(String s) {
        if (s == null || s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(i + 1);
            if (n == '/' || n == '\\' || n == '"' || n == '\'') {
                sb.append(n);
                i++;
            } else if (n == 'u' && i + 5 < s.length()) {
                try {
                    int cp = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                    sb.append((char) cp);
                    i += 5;
                } catch (Throwable t) {
                    sb.append(c);
                }
            } else if (n == 'n') {
                sb.append('\n');
                i++;
            } else if (n == 't') {
                sb.append('\t');
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void addUrl(ArrayList<Hit> out, LinkedHashSet<String> seen,
                               String url) {
        addUrl(out, seen, url, null);
    }

    private static void addUrl(ArrayList<Hit> out, LinkedHashSet<String> seen,
                               String url, String label) {
        if (url == null) {
            return;
        }
        String u = unescape(url).trim().replace("&amp;", "&");
        if (!u.startsWith("http")) {
            return;
        }
        if (!seen.add(u)) {
            return;
        }
        boolean audio = u.contains(".m4a") || u.contains(".mp3");
        String ext = audio ? "m4a" : u.contains(".webm") ? "webm" : "mp4";
        out.add(new Hit(u, ext, audio,
                label != null ? label : (audio ? "Audio" : "Video")));
    }

    // ==================== নেটওয়ার্ক ====================

    static String get(String u) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
            int code = c.getResponseCode();
            if (code < 200 || code >= 400) {
                StreamStore.note("extract http " + code + " " + host(u));
                return null;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                total += n;
                if (total > 4 * 1024 * 1024) {
                    break; // পেজ অনেক বড় হলে থামি
                }
            }
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (c != null) {
                    c.disconnect();
                }
            } catch (Throwable t) {
            }
        }
    }

    /** vm.tiktok.com / fb.watch — ছোট লিংক আসল ঠিকানায় নিয়ে যাই। */
    static String resolveRedirects(String u) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.getResponseCode();
            String real = c.getURL() != null ? c.getURL().toString() : u;
            return real;
        } catch (Throwable t) {
            return u;
        } finally {
            try {
                if (c != null) {
                    c.disconnect();
                }
            } catch (Throwable t) {
            }
        }
    }

    static String instagramApiUrl(String u) {
        String base = u;
        int q = base.indexOf('?');
        if (q >= 0) {
            base = base.substring(0, q);
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + "?__a=1&__d=dis";
    }

    static String host(String url) {
        try {
            String s = url == null ? "" : url.trim();
            int sc = s.indexOf("://");
            if (sc < 0) {
                return "";
            }
            String rest = s.substring(sc + 3);
            int slash = rest.indexOf('/');
            String hp = slash >= 0 ? rest.substring(0, slash) : rest;
            int at = hp.indexOf('@');
            if (at >= 0) {
                hp = hp.substring(at + 1);
            }
            int colon = hp.lastIndexOf(':');
            String h = colon > 0 ? hp.substring(0, colon) : hp;
            return h.toLowerCase();
        } catch (Throwable t) {
            return "";
        }
    }
}
