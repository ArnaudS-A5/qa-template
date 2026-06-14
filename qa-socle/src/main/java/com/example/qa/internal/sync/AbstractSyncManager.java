package com.example.qa.internal.sync;

import java.time.Duration;
import java.util.function.Supplier;
import com.example.qa.api.exception.SyncException;

public abstract class AbstractSyncManager {

	protected abstract <T> T fluentWait(Duration timeout, Supplier<T> condition) throws SyncException;

}
