package com.delivery.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果封装
 * 所有分页接口统一返回这个结构，前端好解析
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;   // 当前页数据
    private long total;        // 总条数
    private long pageNum;      // 当前页码
    private long pageSize;     // 每页条数

    /**
     * 构造分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> p = new PageResult<>();
        p.records = records == null ? Collections.emptyList() : records;
        p.total = total;
        p.pageNum = pageNum;
        p.pageSize = pageSize;
        return p;
    }

    /**
     * 空分页结果（查不到数据时用）
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(Collections.emptyList(), 0, pageNum, pageSize);
    }
}
