package com.asdfstudio.vietfloat;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class VietnameseDetector {
    private static final Pattern MARKED = Pattern.compile(".*[ăâđêôơưĂÂĐÊÔƠƯáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ].*");
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
            "anh","em","chi","toi","ban","minh","khong","duoc","roi","nhe","dang","lam","viec","gio","hom","nay","vao","cho","cua","voi","mot","sao","vay","dau","day","ben","tang","co","phai","nhieu","it","chua","se","can","muon","biet","thay","den","ve","di","luc","tieng","nhan","nguoi"));

    public static boolean isLikelyVietnamese(String text) {
        if (text == null) return false;
        String clean = text.trim();
        if (clean.length() < 3 || clean.length() > 1200 || containsKorean(clean)) return false;
        if (MARKED.matcher(clean).matches()) return true;
        String normalized = Normalizer.normalize(clean, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ','d').replace('Đ','D')
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return false;
        String[] words = normalized.split(" ");
        int hits = 0;
        for (String word : words) if (COMMON.contains(word)) hits++;
        return hits >= 2 || (hits >= 1 && words.length >= 5);
    }

    private static boolean containsKorean(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x3130 && c <= 0x318F)) return true;
        }
        return false;
    }
}
