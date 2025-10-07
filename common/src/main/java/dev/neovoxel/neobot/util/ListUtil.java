package dev.neovoxel.neobot.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListUtil {
    @SafeVarargs
    public static <T> List<T> of(T... e) {
        List<T> list = new ArrayList<>();
        Collections.addAll(list, e);
        return list;
    }
}
