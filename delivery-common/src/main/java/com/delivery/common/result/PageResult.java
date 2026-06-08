package com.delivery.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> p = new PageResult<>();
        p.records = records == null ? Collections.emptyList() : records;
        p.total = total;
        p.pageNum = pageNum;
        p.pageSize = pageSize;
        return p;
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(Collections.emptyList(), 0, pageNum, pageSize);
    }
}
