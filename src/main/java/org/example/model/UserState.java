package org.example.model;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name="userStateTable")
public class UserState {
    @Id
    private Long chatId;
    private boolean showMenu;
    private boolean showCategory;
    private boolean showBucket;
    private boolean askAddress;
    private boolean acceptOrder;
    private boolean addPhone;
    private boolean selectTime;
    private boolean askAddition;
    private boolean changed;
    private int messageId;
    private boolean adminMod;

    public boolean isSelectTime() {
        return selectTime;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public void setSelectTime(boolean selectTime) {
        this.selectTime = selectTime;
    }

    public boolean isAskAddition() {
        return askAddition;
    }

    public void setAskAddition(boolean askAddition) {
        this.askAddition = askAddition;
    }

    public boolean isAddPhone() {
        return addPhone;
    }

    public void setAddPhone(boolean addPhone) {
        this.addPhone = addPhone;
    }

    public boolean isShowMenu() {
        return showMenu;
    }

    public void setShowMenu(boolean showMenu) {
        this.showMenu = showMenu;
    }

    public boolean isShowCategory() {
        return showCategory;
    }

    public void setShowCategory(boolean showCategory) {
        this.showCategory = showCategory;
    }

    public boolean isShowBucket() {
        return showBucket;
    }

    public void setShowBucket(boolean showBucket) {
        this.showBucket = showBucket;
    }

    public boolean isAskAddress() {
        return askAddress;
    }

    public void setAskAddress(boolean askAddress) {
        this.askAddress = askAddress;
    }

    public boolean isAcceptOrder() {
        return acceptOrder;
    }

    public void setAcceptOrder(boolean acceptOrder) {
        this.acceptOrder = acceptOrder;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public boolean isAdminMod() {
        return adminMod;
    }

    public void setAdminMod(boolean adminMod) {
        this.adminMod = adminMod;
    }

    public UserState() {
        this.showMenu = false;
        this.showCategory = false;
        this.showBucket = false;
        this.askAddress = false;
        this.addPhone = false;
        this.acceptOrder = false;
        this.selectTime = false;
        this.askAddition = false;
        this.changed = false;
        this.adminMod = false;
        this.messageId = 0;
    }

}
