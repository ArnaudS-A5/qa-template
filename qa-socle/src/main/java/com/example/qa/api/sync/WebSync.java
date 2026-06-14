package com.example.qa.api.sync;

import java.time.Duration;
import java.util.function.Supplier;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import com.example.qa.api.exception.SyncException;
import com.example.qa.internal.sync.AbstractSyncManager;

public class WebSync extends AbstractSyncManager {

	@Override
	protected <T> T fluentWait(Duration timeout, Supplier<T> condition) throws SyncException {
		return null;
	}

	public void click(By locator) throws SyncException {
	}

	public void type(By locator, String text) throws SyncException {
	}

	public String getText(By locator) throws SyncException {
		return null;
	}

	public void selectByLabel(By locator, String label) throws SyncException {
	}

	public boolean isDisplayed(By locator) throws SyncException {
		return false;
	}

	public boolean isClickable(By locator) throws SyncException {
		return false;
	}

	public WebElement waitForElement(By locator, Duration timeout) throws SyncException {
		return null;
	}

	public void waitForElementToDisappear(By locator, Duration timeout) throws SyncException {
	}

	public boolean isPresent(By locator) throws SyncException {
		return false;
	}
}
