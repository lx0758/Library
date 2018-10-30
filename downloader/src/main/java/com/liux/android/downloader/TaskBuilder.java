package com.liux.android.downloader;

import com.liux.android.downloader.core.DownloaderService;
import com.liux.android.downloader.core.Task;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务建造器(除url外其他参数可选)
 */
public class TaskBuilder {

    private String url;
    private String method;
    private Map<String, List<String>> headers;
    private File dir;
    private String fileName;

    public TaskBuilder(String url) {
        this.url = url;
    }

    public TaskBuilder method(String method) {
        this.method = method;
        return this;
    }

    public TaskBuilder header(String name, List<String> values) {
        if (headers == null) headers = new HashMap<>();
        headers.put(name, values);
        return this;
    }

    public TaskBuilder dir(File dir) {
        this.dir = dir;
        return this;
    }

    public TaskBuilder fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public Task build() {
        return DownloaderService.get().createTask(url, method, headers, dir, fileName);
    }
}
