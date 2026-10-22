package com.mytube.web;

import android.net.Uri;
import java.util.ArrayList;

/**
 * ওয়েব-পেজ নিজে যে মিডিয়া-ফাইল লোড করে (mp4 / m4a / webm / mp3) সেগুলো ধরে রাখে।
 *
 * <p>উদ্দেশ্য: ইউটিউবের বাইরের সাইট (TikTok ইত্যাদি) থেকে ডাউনলোড। আমরা কোনো
 * সাইট-ভিত্তিক স্ক্র্যাপার লিখি না — বরং ব্রাউজার হিসেবে পেজটি খুলে দিই, আর
 * পেজটা নিজে যে ভিডিও-ফাইলটা টানে সেটাই ধরে ফেলি। যে সাইট ফাইলটা লুকায়
 * (blob:, টুকরো-টুকরো স্ট্রিম) সেখানে কিছুই পাওয়া যায় না — তাতে ক্ষতি নেই,
 * ব্যবহারকারী শুধু পেজটা দেখবেন।
 *
 * <p>সাইট-নির্দিষ্ট কোড না থাকায় ইউটিউবের মতোই কিছু ভাঙার আশঙ্কা নেই।
 */
public class MediaSniffer {

    /** একটি পাওয়া মিডিয়া-ফাইল। */
    public static final class M {
        public final String url;
        public final String host;
        public final String ext;    // mp4 / m4a / webm / mp3 / ogg
        public final boolean audio;
        public final long at;

        M(String u, String h, String e, boolean a) {
            url = u;
            host = h;
            ext = e;
            audio = a;
            at = System.currentTimeMillis();
        }

        /** "Video · tiktok.com" / "Audio · cdn.example.com" */
        public String label() {
            return (audio ? "Audio" : "Video") + " · " + host;
        }
    }

    private static final int MAX = 10;
    private static final long ARM_MS = 30L * 60L * 1000L;

    private static final ArrayList<M> list = new ArrayList<M>();
    private static volatile boolean armed = false;
    private static volatile long armedAt = 0L;
    private static volatile long pageTag = 0L;

    /** লিংক খোলার সময়: এই পেজের মিডিয়া খুঁজতে শুরু করি। */
    public static void arm() {
        synchronized (list) {
            list.clear();
        }
        armed = true;
        armedAt = System.currentTimeMillis();
        pageTag++;
    }

    public static void disarm() {
        armed = false;
    }

    public static boolean isArmed() {
        return armed && (System.currentTimeMillis() - armedAt) < ARM_MS;
    }

    public static long pageTag() {
        return pageTag;
    }

    public static ArrayList<M> list() {
        ArrayList<M> out = new ArrayList<M>();
        synchronized (list) {
            out.addAll(list);
        }
        return out;
    }

    public static void clear() {
        synchronized (list) {
            list.clear();
        }
    }

    /**
     * একটি রিকোয়েস্ট দেখা গেল। মিডিয়া-ফাইল মনে হলে তালিকায় যোগ হয় এবং
     * সত্যি ফেরত দেয় (নতুন কিছু যোগ হলে) — নাহলে false।
     */
    public static boolean capture(Uri u) {
        return u != null && capture(u.toString());
    }

    /**
     * স্ট্রিং-ভিত্তিক মূল যুক্তি (অ্যান্ড্রয়েড-মুক্ত, তাই আলাদা টেস্ট করা যায়)।
     * মিডিয়া-ফাইল মনে হলে তালিকায় যোগ করে true ফেরত দেয়।
     */
    public static boolean capture(String full) {
        if (!isArmed() || full == null) {
            return false;
        }
        try {
            java.net.URI uri = new java.net.URI(full);
            String sch = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!sch.equals("http") && !sch.equals("https")) {
                return false;
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (host.length() == 0 || host.indexOf('.') < 0) {
                return false;
            }
            // ইউটিউবের স্ট্রিম StreamStore দেখে — এখানে দরকার নেই
            if (host.endsWith("googlevideo.com") || host.endsWith("youtube.com")
                    || host.endsWith("ytimg.com") || host.endsWith("ggpht.com")
                    || host.endsWith("gstatic.com")) {
                return false;
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            String lower = path.toLowerCase();
            String q = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase();

            // টুকরো/সেগমেন্ট/মিডিয়া-বহির্ভূত জিনিস বাদ
            if (lower.endsWith(".ts") || lower.endsWith(".m4s")
                    || lower.endsWith(".m3u8") || lower.endsWith(".mpd")
                    || lower.endsWith(".vtt") || lower.endsWith(".srt")
                    || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".png") || lower.endsWith(".webp")
                    || lower.endsWith(".gif") || lower.endsWith(".svg")
                    || lower.endsWith(".js") || lower.endsWith(".css")
                    || lower.endsWith(".json") || lower.endsWith(".html")) {
                return false;
            }

            String ext = null;
            boolean audio = false;
            if (lower.endsWith(".mp4") || lower.endsWith(".m4v")
                    || lower.endsWith(".mov") || lower.endsWith(".mkv")) {
                ext = lower.endsWith(".mkv") ? "mkv"
                        : lower.endsWith(".mov") ? "mov"
                        : lower.endsWith(".m4v") ? "m4v" : "mp4";
            } else if (lower.endsWith(".webm")) {
                ext = "webm";
            } else if (lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                ext = "m4a";
                audio = true;
            } else if (lower.endsWith(".mp3")) {
                ext = "mp3";
                audio = true;
            } else if (lower.endsWith(".ogg") || lower.endsWith(".opus")) {
                ext = "ogg";
                audio = true;
            } else if (q.contains("mime_type=video")) {
                ext = "mp4";
            } else if (q.contains("mime_type=audio")) {
                ext = "m4a";
                audio = true;
            }
            if (ext == null) {
                return false;
            }

            synchronized (list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).url.equals(full)) {
                        return false;
                    }
                }
                list.add(0, new M(full, host, ext, audio));
                while (list.size() > MAX) {
                    list.remove(list.size() - 1);
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** এক্সটেনশন অনুযায়ী MIME (MediaStore-এ সঠিক ফোল্ডারে বসার জন্য)। */
    public static String mimeOf(String ext) {
        if (ext == null) {
            return "video/mp4";
        }
        if (ext.equals("mp3")) {
            return "audio/mpeg";
        }
        if (ext.equals("m4a")) {
            return "audio/mp4";
        }
        if (ext.equals("ogg")) {
            return "audio/ogg";
        }
        if (ext.equals("webm")) {
            return "video/webm";
        }
        if (ext.equals("mkv")) {
            return "video/x-matroska";
        }
        if (ext.equals("mov")) {
            return "video/quicktime";
        }
        if (ext.equals("m4v")) {
            return "video/x-m4v";
        }
        return "video/mp4";
    }
}
