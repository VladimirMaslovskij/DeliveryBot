package org.example.service;

import org.example.config.BotConfig;
import org.example.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.ws.rs.BadRequestException;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
@EnableScheduling
@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    final BotConfig config;
    private final long goupId = -4186710535L;
    @Autowired
    private BucketRepository bucketRepository;
    @Autowired
    private GoodRepository goodRepository;
    @Autowired
    private UserStateRepository userStateRepository;
    @Autowired
    private CloudRepository cloudRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/menu", "Меню"));
        listOfCommands.add(new BotCommand("/bucket", "Корзина"));
        listOfCommands.add(new BotCommand("/help", "Информация о нас"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        }
        catch (TelegramApiException e) {
            log.error("Error setting bot's command list" + e.getMessage());
        }
    }
    @Override
    public void onUpdateReceived(Update update) {
            Cloud cloud = getCloud();
            UserState thisState = checkUserState(update);
                if (update.hasMessage() && update.getMessage().hasText()) {
                    textLogic(update, thisState, cloud);
                } else if (update.hasCallbackQuery()) {
                    callbackLogic(update, thisState);
                } else if (update.getMessage().hasContact()) {
                    phoneLogic(update, thisState);
                } else if (update.getMessage().hasPhoto()) {
                    photoLogic(update, thisState);
                }
    }
    private void textLogic(Update update, UserState thisState, Cloud cloud) {
        if (!(update.getMessage().getChatId() == goupId)) {
            if (update.getMessage().getText().equals("/menu") || update.getMessage().getText()
                    .equals("/start")) {
                if (checkUserIsAdmin(thisState.getChatId(), cloud)) {
                    thisState.setAdminMod(true);
                    userStateRepository.save(thisState);
                    adminLogic(thisState, cloud);
                    breakState(thisState);
                } else {
                    if (thisState.getMessageId() != 0)
                        deleteMessage(thisState.getChatId(), thisState.getMessageId());
                    breakState(thisState);
                    showMenu(thisState, cloud);
                }
            }
            if (update.getMessage().getText().equals("/sets")) {
                breakState(thisState);
            }
            if (update.getMessage().getText().equals("/actions")) {
                breakState(thisState);
            }
            if (update.getMessage().getText().equals("/bucket")) {
                goToBucket(thisState, thisState.getMessageId());
                breakState(thisState);
            }
            if (update.getMessage().getText().equals("/help")) {
                breakState(thisState);
            } else if (thisState.isAdminMod() && thisState.isShowCategory()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String catName = update.getMessage().getText();
                Category category = getCategoryFromId((long) cloud.getCatCount());
                category.setName(catName);
                categoryRepository.save(category);
                sendAdminMessageAddition(thisState);
            } else if (thisState.isAdminMod() && thisState.isAskAddition()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String additionAsk = update.getMessage().getText();
                Category category = getCategoryFromId((long) cloud.getCatCount());
                category.setAdditionAsk(additionAsk);
                category.setAdditionVars("");
                categoryRepository.save(category);
                sendMessageAskAdmin(thisState, "Введите количество вариантов выбора параметра");
                thisState.setSelectTime(true);
                thisState.setAskAddition(false);
                userStateRepository.save(thisState);
            } else if (thisState.isAdminMod() && thisState.isSelectTime() && !thisState.isAcceptOrder()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String additionCount = update.getMessage().getText();
                Category category = getCategoryFromId((long) cloud.getCatCount());
                category.setVarsCount(additionCount);
                categoryRepository.save(category);
                sendAdminMessageFirstParam(thisState);
            } else if (thisState.isAdminMod() && thisState.isAskAddress()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String param = update.getMessage().getText();
                sendAdminMessageNextParam(thisState, param);
            } else if (thisState.isAdminMod() && thisState.isAddPhone() && !thisState.isAcceptOrder()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String goodName = update.getMessage().getText();
                Good good = getGoodById((long) cloud.getGoodCoutn());
                good.setName(goodName);
                goodRepository.save(good);
                sendMessageAskAdmin(thisState, "Введите описание товара");
                thisState.setAddPhone(false);
                thisState.setAcceptOrder(true);
                userStateRepository.save(thisState);
            } else if (thisState.isAdminMod() && thisState.isAcceptOrder() & !thisState.isSelectTime()
            && !thisState.isAddPhone()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String goodInfo = update.getMessage().getText();
                Good good = getGoodById((long) cloud.getGoodCoutn());
                good.setInfo(goodInfo);
                goodRepository.save(good);
                sendMessageAskAdmin(thisState, "Отправьте фото товара");
            } else if (thisState.isAdminMod() && thisState.isSelectTime() && thisState.isAcceptOrder()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String goodPrice = update.getMessage().getText();
                Good good = getGoodById((long) cloud.getGoodCoutn());
                good.setPrice(Float.valueOf(goodPrice));
                goodRepository.save(good);
                sendMessageAskAdmin(thisState, "Товар успешно создан");
                thisState.setSelectTime(false);
                thisState.setAcceptOrder(false);
                userStateRepository.save(thisState);
            } else if (thisState.isAdminMod() && thisState.isAddPhone() && thisState.isAcceptOrder()) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String goodPrice = update.getMessage().getText();
                sendAdminMessageNextPrice(thisState, goodPrice);
            } else if (thisState.isAddPhone() && !thisState.isChanged() && !thisState.isAdminMod()) {
                String phone = update.getMessage().getText();
                checkPhoneFormat(thisState, phone);
            } else if (thisState.isAddPhone() && thisState.isChanged() && !thisState.isAdminMod()) {
                String phone = update.getMessage().getText();
                changePhone(thisState, phone);
            } else if (thisState.isAskAddress() && !thisState.isChanged() && !thisState.isAdminMod()) {
                setAddressFromText(update, thisState);
            } else if (thisState.isAskAddress() && thisState.isChanged() && !thisState.isAdminMod()) {
                changeAddressFromText(update, thisState);
            } else if (thisState.isAskAddition() && !thisState.isChanged() && !thisState.isAdminMod()) {
                setAdditionFromText(update, thisState);
            } else if (thisState.isAskAddition() && thisState.isChanged() && !thisState.isAdminMod()) {
                chageAdditionFromText(update, thisState);
            } else if (thisState.isSelectTime() && !thisState.isChanged() && !thisState.isAdminMod()) {
                String time = update.getMessage().getText();
                checkTimeFormat(thisState, time);
            } else if (thisState.isSelectTime() && thisState.isChanged() && !thisState.isAdminMod()) {
                String time = update.getMessage().getText();
                changeTime(thisState, time);
            }
        }
    }
    private void callbackLogic(Update update, UserState thisState) {
        String callbackData = update.getCallbackQuery().getData();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        if (thisState.isAdminMod()) {
            if (callbackData.startsWith("usermod")) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                thisState.setAdminMod(false);
                userStateRepository.save(thisState);
                showMenu(thisState, getCloud());
            } else if (callbackData.startsWith("addCategory")) {
                Cloud cloud = getCloud();
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                Category category = new Category();
                int count = cloud.getCatCount() + 1;
                category.setId(count);
                cloud.setCatCount(count);
                categoryRepository.save(category);
                cloudRepository.save(cloud);
                sendAdminMessageAddCategory(thisState);
            } else if (callbackData.startsWith("addGood")) {
                Cloud cloud = getCloud();
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                int goodCount = cloud.getGoodCoutn() + 1;
                Good good = new Good();
                good.setId((long) goodCount);
                good.setAdditionalPars("");
                cloud.setGoodCoutn(goodCount);
                cloudRepository.save(cloud);
                goodRepository.save(good);
                thisState.setAddPhone(true);
                userStateRepository.save(thisState);
                sendMessageAskAdmin(thisState, "Введите название товара");
            } else if (callbackData.startsWith("changeCategory")) {
                printCategoriesToChange(thisState);
            } else if (callbackData.startsWith("changeGood")) {

            } else if (callbackData.startsWith("CatChange")) {
                String catId = callbackData.split("/")[1];
                printChangeCatVars(catId, thisState);
            } else if (callbackData.startsWith("additionYes")) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                Category category =getCategoryFromId((long) getCloud().getCatCount());
                category.setAdditionalPar(true);
                categoryRepository.save(category);
                thisState.setAskAddition(true);
                sendMessageAskAdmin(thisState, "Введите название дополнительного параметра");
                userStateRepository.save(thisState);
            } else if (callbackData.startsWith("additionNo")) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                sendMessage(thisState.getChatId(), "Категория успешно добавлена.");
                userStateRepository.save(thisState);
            } else if (callbackData.startsWith("ChoiceCat")) {
                deleteMessage(thisState.getChatId(), thisState.getMessageId());
                String categoryId = callbackData.split("/")[1];
                Good good = getGoodById((long) getCloud().getGoodCoutn());
                good.setCategory(categoryId);
                goodRepository.save(good);
                Category category = getCategoryFromId(Long.valueOf(categoryId));
                if (category.isAdditionalPar()) {
                    sendMessageAskAdmin(thisState, "Введите цену для параметра № 1 ("
                            + category.getParametr(0) + ")");
                    thisState.setAddPhone(true);
                } else {
                    sendMessageAskAdmin(thisState, "Введите цену товара");
                    thisState.setSelectTime(true);
                }
                userStateRepository.save(thisState);
            }
        } else if (callbackData.startsWith("Category")) {
            String categoryId = callbackData.split("/")[1];
            showCategories(thisState, messageId, categoryId);
        } else if (callbackData.startsWith("InBucket")) {
            String goodId = callbackData.split(":")[1];
            Category category = getCategoryFromId(Long.valueOf(getGoodById(Long.valueOf(goodId)).getCategory()));
            if (category.isAdditionalPar()) {
                addAdditionalGoodInBucket(callbackData, thisState, messageId);
            } else {
                addGoodInBucket(callbackData, thisState, messageId);
            }
        } else if (callbackData.startsWith("InAddBucket")) {
            System.out.println("mi zdes");
            deleteMessage(thisState.getChatId(), thisState.getMessageId());
            createBucketWitchPar(thisState, Long.valueOf(callbackData.split(":")[1]),
                    Float.valueOf(callbackData.split(":")[3]));
            String strIds = callbackData.split(":")[2];
            String[] strArray = strIds.split("/");
            ArrayList<Long> list = new ArrayList<>();
            for (String str:strArray) {
                list.add(Long.valueOf(str));
            }
            int i = list.indexOf(Long.valueOf(callbackData.split(":")[1]));
            int userMesId = printGoodFromCategoryId(thisState, strIds, messageId, i);
            thisState.setMessageId(userMesId);
            userStateRepository.save(thisState);
        } else if (callbackData.startsWith("Order")) {
            buyGoodRightNow(callbackData, thisState, messageId);
        } else if (callbackData.startsWith("GoBucket")) {
            goToBucket(thisState, messageId);
        } else if (callbackData.startsWith("Accept")) {
            makeOrder(thisState, messageId);
        } else if (callbackData.startsWith("Pickup") && !thisState.isChanged()) {
            setPickup(thisState);
        } else if (callbackData.startsWith("Pickup") && thisState.isChanged()) {
            changePickup(thisState, chatId);
        } else if (callbackData.startsWith("Skip") && !thisState.isChanged()) {
            skipAddition(thisState);
        } else if (callbackData.startsWith("Skip") && thisState.isChanged()) {
            changeAddition(thisState);
        } else if (callbackData.startsWith("now") && !thisState.isChanged()) {
            setTimeNow(thisState);
        } else if (callbackData.startsWith("now") && thisState.isChanged()) {
            changeTimeNow(thisState, chatId);
        } else if (callbackData.startsWith("finish")) {
            createAndMakeOrder(thisState, chatId);
        } else if (callbackData.startsWith("changePhone")) {
            changePhoneFromCallback(thisState, messageId);
        } else if (callbackData.startsWith("changeAddress")) {
            changeAddressFromCallback(thisState);
        } else if (callbackData.startsWith("changeTime")) {
            changeTimeFromCallback(thisState);
        } else if (callbackData.startsWith("changeAddition")) {
            changeAdditionFromCallback(thisState);
        } else if (callbackData.startsWith("Menu")) {
            deleteMessage(chatId, (int) messageId);
            showMenu(thisState, getCloud());
        } else if (callbackData.startsWith("Right")) {
            tapRight(callbackData, thisState, messageId);
        } else if (callbackData.startsWith("DeleteGood")) {
            deleteGoodFromBucket(callbackData, chatId, thisState, messageId);
        } else if (callbackData.startsWith("Left")) {
            tapLeft(callbackData, thisState, messageId);
        } else if (callbackData.startsWith("deleteOrder")) {
            deleteMessage(chatId, thisState.getMessageId());
            breakState(thisState);
            sendMessage(chatId, "Ваш заказ успешно удален");
            showMenu(thisState, getCloud());
        } else if (callbackData.startsWith("BuyAdd")) {
            deleteMessage(thisState.getChatId(), thisState.getMessageId());
            createBucketWitchPar(thisState, Long.valueOf(callbackData.split(":")[1]),
                   Float.valueOf(callbackData.split(":")[2]));
            showBucket(thisState, messageId);
        }
    }
    private void phoneLogic(Update update, UserState thisState) {
        if (!thisState.isChanged()) {
            setPhone(update, thisState);
        } else {
            changePhoneFromContact(update, thisState);
        }
    }
    private void photoLogic(Update update, UserState thisState) {
        if (thisState.isAdminMod() && thisState.isAcceptOrder()) {
            deleteMessage(thisState.getChatId(), thisState.getMessageId());
            List<PhotoSize> photos = update.getMessage().getPhoto();
            GetFile getFile = new GetFile(photos.get(2).getFileId());
            Good good = getGoodById((long) getCloud().getGoodCoutn());
            try {
                org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
                downloadFile(file, new java.io.File("C:/photos/" + good.getId() + ".png"));
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            sendAdminMessageToChoiceCat(thisState);
        }
    }
    private void adminLogic(UserState thisState, Cloud cloud) {
        sendAdminMessage(thisState);
    }
    private List<Category> getCategories() {
        Iterable<Category> categories = categoryRepository.findAll();
        List<Category> catList = new ArrayList<>();
        for (Category cat: categories) {
            catList.add(cat);
        }
        return catList;
    }
    private void setAddressFromText(Update update, UserState thisState) {
        String address = update.getMessage().getText();
        Order order = getOrderById(thisState.getChatId());
        order.setAddress(address);
        orderRepository.save(order);
        thisState.setAskAddress(false);
        thisState.setAskAddition(true);
        int userMesId = sendMessageWithAddition(thisState.getChatId(), "Введите дополнительные пожелания к заказу" +
                "или нажмите \"Пропустить\"", thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void changeAddressFromText(Update update, UserState thisState) {
        String address = update.getMessage().getText();
        Order order = getOrderById(thisState.getChatId());
        order.setAddress(address);
        orderRepository.save(order);
        thisState.setAskAddress(false);
        thisState.setChanged(false);
        int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                        " оформить заказ, или измените необходимые параметры заказа.",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void setAdditionFromText(Update update, UserState thisState) {
        String addition = update.getMessage().getText();
        Order order = getOrderById(thisState.getChatId());
        order.setAddition(addition);
        orderRepository.save(order);
        thisState.setAskAddition(false);
        thisState.setSelectTime(true);
        int userMesId = sendMessageWithTime(thisState.getChatId(), "Введите желаемое время доставки в " +
                        "формате \"20:00\"" +
                        "или нажмите \"По готовности\"",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void chageAdditionFromText(Update update, UserState thisState) {
        String addition = update.getMessage().getText();
        Order order = getOrderById(thisState.getChatId());
        order.setAddition(addition);
        orderRepository.save(order);
        thisState.setAskAddition(false);
        thisState.setChanged(false);
        sendMessage(thisState.getChatId(), "Пожелания успешно изменены");
        int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                        " оформить заказ, или измените необходимые параметры заказа.",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void addGoodInBucket(String callbackData, UserState thisState, long messageId) {
        Long goodId = Long.valueOf(callbackData.split(":")[1]);
        Good good = getGoodById(goodId);
        Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
        if (category.isAdditionalPar()) {
            Float priceGood = Float.valueOf(callbackData.split(":")[3]);
            createBucketWitchPar(thisState, goodId, priceGood);
        } else {
            createBucket(thisState, goodId);
        }
        String strIds = callbackData.split(":")[2];
        String[] strArray = strIds.split("/");
        ArrayList<Long> list = new ArrayList<>();
        for (String str:strArray) {
            list.add(Long.valueOf(str));
        }
        int i = list.indexOf(goodId);
        int userMesId = printGoodFromCategoryId(thisState, strIds, messageId, i);
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void printCategoriesToChange(UserState state) {
        deleteMessage(state.getChatId(), state.getMessageId());
        List<Category> list = getCategories();
        SendMessage message = new SendMessage();
        message.setChatId(state.getChatId());
        message.setText("Выберите категорию для изменения (удаления)");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        for (Category category:list) {
            List<InlineKeyboardButton> newRow = new ArrayList<>();
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .callbackData("CatChange/" + category.getId())
                    .text(category.getName())
                    .build();
            newRow.add(button);
            rowsLine.add(newRow);
        }
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private void printChangeCatVars(String catId, UserState state) {
        deleteMessage(state.getChatId(), state.getMessageId());
        SendMessage message = new SendMessage();
        message.setChatId(state.getChatId());
        Category category = getCategoryFromId(Long.valueOf(catId));
        message.setText("Выберите действие для категории \"" + category.getName() + "\":");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .callbackData("changeCatAdd/" + catId)
                .text("Изменить/Добавить доп параметры")
                .build();
        row.add(button);
        rowsLine.add(row);
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton button2 = InlineKeyboardButton.builder()
                .callbackData("RenameCat/" + catId)
                .text("✍️ Переименовать")
                .build();
        row2.add(button2);
        InlineKeyboardButton button3 = InlineKeyboardButton.builder()
                .callbackData("DeleteCat/" + catId)
                .text("❌ Удалить")
                .build();
        row2.add(button3);
        rowsLine.add(row2);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private void addAdditionalGoodInBucket(String callbackData, UserState thisState, long messageId) {
        deleteMessage(thisState.getChatId(), thisState.getMessageId());
        String goodId = callbackData.split(":")[1];
        String strIds = callbackData.split(":")[2];
        Good good = getGoodById(Long.valueOf(goodId));
        Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
        SendMessage message = new SendMessage();
        message.setChatId(thisState.getChatId());
        message.setText("Выберите дополнительный параметр: \"" + category.getAdditionAsk() + "\"");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        int varsCount = Integer.parseInt(category.getVarsCount());
        int res = 0;
        for (int j = 0; j < varsCount/2; j++) {
            List<InlineKeyboardButton> newRow = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                        .callbackData("InAddBucket:" + good.getId() + ":" + strIds + ":"
                                + good.getParametr(res))
                        .text("✅ " + category.getParametr(res) + " (" + good.getParametr(res) + " р.)")
                        .build();
                res = res + 1;
                newRow.add(inlineKeyboardButton);
            }
            rowsLine.add(newRow);
        }
        if (varsCount % 2 != 0) {
            List<InlineKeyboardButton> newRow = new ArrayList<>();
            InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                    .callbackData("InAddBucket:" + good.getId() + ":" + strIds + ":"
                            + good.getParametr(res))
                    .text("✅ " + category.getParametr(res) + " (" + good.getParametr(res) + " р.)")
                    .build();
            newRow.add(inlineKeyboardButton);
            rowsLine.add(newRow);
        }
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        thisState.setMessageId(execute.getMessageId());
        userStateRepository.save(thisState);
    }
    private void buyGoodRightNow(String callbackData, UserState thisState, long messageId) {
        Long goodId = Long.valueOf(callbackData.split("/")[1]);
        Good good = getGoodById(goodId);
        Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
        if (category.isAdditionalPar()) {
            deleteMessage(thisState.getChatId(), thisState.getMessageId());
            SendMessage message = new SendMessage();
            message.setChatId(thisState.getChatId());
            message.setText("Выберите дополнительный параметр: \"" + category.getAdditionAsk() + "\"");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
            int varsCount = Integer.parseInt(category.getVarsCount());
            int res = 0;
            for (int j = 0; j < varsCount/2; j++) {
                List<InlineKeyboardButton> newRow = new ArrayList<>();
                for (int k = 0; k < 2; k++) {
                    InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                            .callbackData("BuyAdd:" + good.getId() + ":" + good.getParametr(res))
                            .text("✅ " + category.getParametr(res) + " (" + good.getParametr(res) + " р.)")
                            .build();
                    res = res + 1;
                    newRow.add(inlineKeyboardButton);
                }
                rowsLine.add(newRow);
            }
            if (varsCount % 2 != 0) {
                List<InlineKeyboardButton> newRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                        .callbackData("BuyAdd:" + good.getId() + ":" + good.getParametr(res))
                        .text("✅ " + category.getParametr(res) + " (" + good.getParametr(res) + " р.)")
                        .build();
                newRow.add(inlineKeyboardButton);
                rowsLine.add(newRow);
            }
            markup.setKeyboard(rowsLine);
            message.setReplyMarkup(markup);
            Message execute;
            try {
                execute = execute(message);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            thisState.setMessageId(execute.getMessageId());
            userStateRepository.save(thisState);
        } else {
            createBucket(thisState, goodId);
            thisState.setShowCategory(false);
            thisState.setShowBucket(true);
            userStateRepository.save(thisState);
            showBucket(thisState, messageId);
        }
    }
    private void goToBucket(UserState thisState, long messageId) {
        thisState.setShowCategory(false);
        thisState.setShowBucket(true);
        userStateRepository.save(thisState);
        showBucket(thisState, messageId);
    }
    private void makeOrder(UserState thisState, long messageId) {
        thisState.setShowBucket(false);
        thisState.setAddPhone(true);
        int userMessageId = sendMessageWithAddPhone(thisState.getChatId(), "Введите номер телефона" +
                " для связи или нажмите кнопку \"Добавить номер\"", messageId);
        thisState.setMessageId(userMessageId);
        userStateRepository.save(thisState);
    }
    private void setPickup(UserState thisState) {
        Order order = getOrderById(thisState.getChatId());
        order.setAddress("Самовывоз");
        orderRepository.save(order);
        thisState.setAskAddress(false);
        thisState.setAskAddition(true);
        int userMesId = sendMessageWithAddition(thisState.getChatId(), "Введите дополнительные пожелания к заказу" +
                "или нажмите \"Пропустить\"", thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void changePickup(UserState thisState, Long chatId) {
        Order order = getOrderById(thisState.getChatId());
        order.setAddress("Самовывоз");
        orderRepository.save(order);
        thisState.setAskAddress(false);
        thisState.setChanged(false);
        sendMessage(chatId, "Аддрес успешно изменен");
        int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                        " оформить заказ, или измените необходимые параметры заказа.",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void skipAddition(UserState thisState) {
        Order order = getOrderById(thisState.getChatId());
        order.setAddition("-");
        orderRepository.save(order);
        thisState.setAskAddition(false);
        thisState.setSelectTime(true);
        int userMesId = sendMessageWithTime(thisState.getChatId(), "Введите желаемое время доставки в " +
                        "формате \"20:00\"" +
                        "или нажмите \"По готовности\"",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void changeAddition(UserState thisState) {
        Order order = getOrderById(thisState.getChatId());
        order.setAddition("-");
        orderRepository.save(order);
        thisState.setAskAddition(false);
        thisState.setChanged(false);
        int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                        " оформить заказ, или измените необходимые параметры заказа.",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void setTimeNow(UserState thisState) {
        Order order = getOrderById(thisState.getChatId());
        order.setTime("По готовности");
        orderRepository.save(order);
        thisState.setSelectTime(false);
        thisState.setAcceptOrder(true);
        int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                        " оформить заказ, или измените необходимые параметры заказа.",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void changeTimeNow(UserState thisState, long chatId) {
        Order order = getOrderById(thisState.getChatId());
        order.setTime("По готовности");
        orderRepository.save(order);
        thisState.setSelectTime(false);
        thisState.setChanged(false);
        sendMessage(chatId, "Время заказа успешно изменено");
        int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                        " оформить заказ, или измените необходимые параметры заказа.",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void createAndMakeOrder(UserState thisState, long chatId) {
        Order order = getOrderById(thisState.getChatId());
        Bucket bucket = getBucketById(thisState.getChatId());
        bucketRepository.deleteById(thisState.getChatId());
        thisState.setAcceptOrder(false);
        sendMessageAboutOrderToChat(bucket, order);
        sendMessage(chatId, "Ваш заказ оформлен, начинаем готовить!)" +
                "\nОжидайте звонка оператора");
        deleteMessage(chatId, thisState.getMessageId());
        thisState.setMessageId(0);
        userStateRepository.save(thisState);
    }
    private void changePhoneFromCallback(UserState thisState, long messageId) {
        int userMessageId = sendMessageWithAddPhone(thisState.getChatId(), "Введите номер телефона" +
                " для связи или нажмите кнопку \"Добавить номер\"", messageId);
        thisState.setMessageId(userMessageId);
        thisState.setChanged(true);
        thisState.setAddPhone(true);
        userStateRepository.save(thisState);
    }
    private void changeAddressFromCallback(UserState thisState) {
        int userMessageId = sendMessageWithPickup(thisState.getChatId(), "Введите адрес " +
                "доставки или нажмите \"Самовывоз\" ", thisState.getMessageId());
        thisState.setMessageId(userMessageId);
        thisState.setChanged(true);
        thisState.setAskAddress(true);
        userStateRepository.save(thisState);
    }
    private void changeTimeFromCallback(UserState thisState) {
        int userMesId = sendMessageWithTime(thisState.getChatId(), "Введите желаемое время доставки в " +
                        "формате \"20:00\"" +
                        "или нажмите \"По готовности\"",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        thisState.setChanged(true);
        thisState.setSelectTime(true);
        userStateRepository.save(thisState);
    }
    private void changeAdditionFromCallback(UserState thisState) {
        int userMesId = sendMessageWithAddition(thisState.getChatId(), "Введите дополнительные пожелания к заказу" +
                "или нажмите \"Пропустить\"", thisState.getMessageId());
        thisState.setMessageId(userMesId);
        thisState.setChanged(true);
        thisState.setAskAddition(true);
        userStateRepository.save(thisState);
    }
    private void tapRight(String callbackData, UserState thisState, long messageId) {
        Long lastId = Long.valueOf(callbackData.split(":")[1]);
        String strIds = callbackData.split(":")[2];
        String[] idArray = strIds.split("/");
        List<Long> listIds = new ArrayList<>();
        for (String str:idArray) {
            listIds.add(Long.valueOf(str));
        }
        int i = 0;
        if (listIds.indexOf(lastId) >= listIds.size() - 1)
            i = 0;
        else
            i = listIds.indexOf(lastId) + 1;
        int userMesId = printGoodFromCategoryId(thisState, strIds, messageId, i);
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void tapLeft(String callbackData, UserState thisState, long messageId) {
        Long lastId = Long.valueOf(callbackData.split(":")[1]);
        String strIds = callbackData.split(":")[2];
        String[] idArray = strIds.split("/");
        List<Long> listIds = new ArrayList<>();
        for (String str:idArray) {
            listIds.add(Long.valueOf(str));
        }
        int i = 0;
        if (listIds.indexOf(lastId) == 0)
            i = listIds.size() - 1;
        else
            i = listIds.indexOf(lastId) - 1;
        int userMesId = printGoodFromCategoryId(thisState, strIds, messageId, i);
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void deleteGoodFromBucket(String callbackData, long chatId, UserState thisState, long messageId) {
        String delId = callbackData.split("/")[1];
        Good good = getGoodById(Long.valueOf(delId));
        Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
        Bucket bucket = getBucketById(chatId);
        if (category.isAdditionalPar()) {
            String delPrice = callbackData.split("/")[2];
            bucket.deleteAdditionalGood(delId, delPrice);
        } else {
            bucket.deleteGood(delId);
            bucket.deletePrice(good.getPrice());
        }
        bucketRepository.save(bucket);
        showBucket(thisState, messageId);
    }
    private void setPhone(Update update, UserState thisState) {
        String phoneNumber = update.getMessage().getContact().getPhoneNumber();
        Order order = new Order();
        order.setId(thisState.getChatId());
        order.setPhoneNumber(phoneNumber);
        order.setFinished(false);
        orderRepository.save(order);
        thisState.setAddPhone(false);
        thisState.setAskAddress(true);
        int userMesId = sendMessageWithPickup(thisState.getChatId(), "Номер телефона успешно добавлен, введите адрес" +
                        "доставки или нажмите \"Самовывоз\": ",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void changePhoneFromContact(Update update, UserState thisState) {
        String phoneNumber = update.getMessage().getContact().getPhoneNumber();
        Order order = getOrderById(thisState.getChatId());
        order.setPhoneNumber(phoneNumber);
        orderRepository.save(order);
        thisState.setAddPhone(false);
        thisState.setChanged(false);
        thisState.setAcceptOrder(true);
        sendMessage(thisState.getChatId(), "Номер телефона успешно изменен");
        int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                        " оформить заказ, или измените необходимые параметры заказа.",
                thisState.getMessageId());
        thisState.setMessageId(userMesId);
        userStateRepository.save(thisState);
    }
    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        executeMessage(message);
    }
    private void sendAdminMessage(UserState state) {
        deleteMessage(state.getChatId(), state.getMessageId());
        SendMessage message = new SendMessage();
        message.setChatId(state.getChatId());
        message.setText("Доброго времени суток, Администратор" +
                "\nВыберите режим работы.");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .callbackData("usermod")
                .text("Режим пользователя")
                .build();
        InlineKeyboardButton inlineKeyboardButton2 = InlineKeyboardButton.builder()
                .callbackData("addCategory")
                .text("Добавить категорию")
                .build();
        InlineKeyboardButton inlineKeyboardButton3 = InlineKeyboardButton.builder()
                .callbackData("changeCategory")
                .text("Изменить категорию")
                .build();
        InlineKeyboardButton inlineKeyboardButton4 = InlineKeyboardButton.builder()
                .callbackData("addGood")
                .text("Добавить товар")
                .build();
        InlineKeyboardButton inlineKeyboardButton5 = InlineKeyboardButton.builder()
                .callbackData("changeGood")
                .text("Изменить (удалить) товар")
                .build();
        row.add(inlineKeyboardButton);
        row2.add(inlineKeyboardButton2);
        row3.add(inlineKeyboardButton3);
        row4.add(inlineKeyboardButton4);
        row5.add(inlineKeyboardButton5);
        rowsLine.add(row);
        rowsLine.add(row2);
        rowsLine.add(row3);
        rowsLine.add(row4);
        rowsLine.add(row5);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private void sendAdminMessageAddCategory(UserState state) {
        SendMessage message = new SendMessage();
        message.setChatId(state.getChatId());
        message.setText("Введите название категории");
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        state.setShowCategory(true);
        userStateRepository.save(state);
    }
    private void sendAdminMessageAddition(UserState state) {
        SendMessage message = new SendMessage();
        message.setChatId(state.getChatId());
        message.setText("Добавить категории дополнительный параметр?");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .callbackData("additionYes")
                .text("Да")
                .build();
        InlineKeyboardButton inlineKeyboardButton2 = InlineKeyboardButton.builder()
                .callbackData("additionNo")
                .text("Нет")
                .build();
        row.add(inlineKeyboardButton);
        row.add(inlineKeyboardButton2);
        rowsLine.add(row);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        state.setShowCategory(false);
        userStateRepository.save(state);
    }
    private void sendMessageAskAdmin(UserState state, String text) {
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(state.getChatId());
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private void sendAdminMessageFirstParam(UserState state) {
        SendMessage message = new SendMessage();
        message.setText("Введите значение для параметра № 1");
        message.setChatId(state.getChatId());
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setSelectTime(false);
        state.setAskAddress(true);
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private void sendAdminMessageNextParam(UserState state, String param) {
        Category category = getCategoryFromId((long) getCloud().getCatCount());
        String params = category.getAdditionVars();
        int varsCount = Integer.parseInt(category.getVarsCount());
        SendMessage message = new SendMessage();
        message.setChatId(state.getChatId());
        if (params.equals("")) {
            System.out.println("Вводим первый параметр");
            category.setAdditionVars(param);
            message.setText("Введите значение для параметра № 2");
            categoryRepository.save(category);
        } else if (params.split("/").length + 1 < varsCount) {
            System.out.println("Вводим следующий параметр");
            String newParam = params + "/" + param;
            category.setAdditionVars(newParam);
            categoryRepository.save(category);
            int number = newParam.split("/").length + 1;
            message.setText("Введите значение для параметра № " + number);
        } else if (params.split("/").length + 1 == varsCount) {
            String newParam = params + "/" + param;
            category.setAdditionVars(newParam);
            categoryRepository.save(category);
            message.setText("Категория " + category.getName() + " успешно создана.");
            state.setAskAddress(false);
        }
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private void sendAdminMessageNextPrice(UserState state, String goodPrice) {
        Category category = getCategoryFromId((long) getCloud().getCatCount());
        Good good = getGoodById((long) getCloud().getGoodCoutn());
        String paramPrice = good.getAdditionalPars();
        int varsCount = Integer.parseInt(category.getVarsCount());
        SendMessage message = new SendMessage();
        message.setChatId(state.getChatId());
        if (paramPrice.equals("")) {
            System.out.println("Вводим первый параметр");
            good.setAdditionalPars(goodPrice);
            message.setText("Введите цену для параметра № 2 (" + category.getParametr(1) + ")");
            goodRepository.save(good);
        } else if (paramPrice.split("/").length + 1 < varsCount) {
            System.out.println("Вводим следующий параметр");
            String newParam = paramPrice + "/" + goodPrice;
            good.setAdditionalPars(newParam);
            goodRepository.save(good);
            int number = newParam.split("/").length + 1;
            message.setText("Введите значение для параметра № " + number + "(" +
                    category.getParametr(number-1) + ")");
        } else if (paramPrice.split("/").length + 1 == varsCount) {
            String newParam = paramPrice + "/" + goodPrice;
            good.setAdditionalPars(newParam);
            goodRepository.save(good);
            message.setText("Товар " + good.getName() + " успешно создан.");
            state.setAcceptOrder(false);
            state.setAddPhone(false);
        }
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private void sendAdminMessageToChoiceCat(UserState state) {
        SendMessage message = new SendMessage();
        message.setText("Выберите категорию:");
        message.setChatId(state.getChatId());
        List<Category> list = getCategories();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton inlineKeyboardButtonDriver = InlineKeyboardButton.builder()
                    .callbackData("ChoiceCat/" + String.valueOf(list.get(i).getId()))
                    .text(list.get(i).getName())
                    .build();
            row.add(inlineKeyboardButtonDriver);
            rowsLine.add(row);
        }
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        state.setMessageId(execute.getMessageId());
        userStateRepository.save(state);
    }
    private Integer sendMessageWithAddPhone(long chatId, String text, long messageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setOneTimeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        KeyboardButton button = new KeyboardButton();
        button.setRequestContact(true);
        button.setText("Добавить номер\uD83C\uDFC1");
        row.add(button);
        keyboardRows.add(row);
        keyboardMarkup.setKeyboard(keyboardRows);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);
        deleteMessage(chatId, (int) messageId);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return execute.getMessageId();
    }
    private int sendMessageWithPickup(long chatId, String text, int messageid) {
        deleteMessage(chatId, messageid);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .callbackData("Pickup")
                .text("Самовывоз")
                .build();
        row.add(inlineKeyboardButton);
        rowsLine.add(row);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return execute.getMessageId();
    }
    private int sendMessageWithAddition(long chatId, String text, int messageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .callbackData("Skip")
                .text("Пропустить")
                .build();
        row.add(inlineKeyboardButton);
        rowsLine.add(row);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        deleteMessage(chatId, messageId);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return execute.getMessageId();
    }
    private int sendMessageWithTime(long chatId, String text, int messageId) {
        deleteMessage(chatId, messageId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .callbackData("now")
                .text("По готовности")
                .build();
        row.add(inlineKeyboardButton);
        rowsLine.add(row);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return execute.getMessageId();
    }
    private int sendMessageWithAcceptOrder(long chatId, String text, int messageId) {
        deleteMessage(chatId, messageId);
        SendMessage message = new SendMessage();
        Order order = getOrderById(chatId);
        message.setChatId(chatId);
        message.setText(text + "\nАдрес: " + order.getAddress() + "\nТелефон: " + order.getPhoneNumber() +
                "\nВремя доставки: " + order.getTime() + "\nПожелания: " + order.getAddition());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .callbackData("finish")
                .text("Оформить заказ")
                .build();
        InlineKeyboardButton inlineKeyboardButton1 = InlineKeyboardButton.builder()
                .callbackData("changePhone")
                .text("Изменить номер")
                .build();
        InlineKeyboardButton inlineKeyboardButton2 = InlineKeyboardButton.builder()
                .callbackData("changeAddress")
                .text("Изменить адрес")
                .build();
        InlineKeyboardButton inlineKeyboardButton3 = InlineKeyboardButton.builder()
                .callbackData("changeTime")
                .text("Изменить время")
                .build();
        InlineKeyboardButton inlineKeyboardButton4 = InlineKeyboardButton.builder()
                .callbackData("changeAddition")
                .text("Изменить пожелания")
                .build();
        InlineKeyboardButton inlineKeyboardButton5 = InlineKeyboardButton.builder()
                .callbackData("deleteOrder")
                .text("Отменить заказ")
                .build();
        row.add(inlineKeyboardButton);
        row1.add(inlineKeyboardButton1);
        row2.add(inlineKeyboardButton2);
        row3.add(inlineKeyboardButton3);
        row4.add(inlineKeyboardButton4);
        row5.add(inlineKeyboardButton5);
        rowsLine.add(row);
        rowsLine.add(row1);
        rowsLine.add(row2);
        rowsLine.add(row3);
        rowsLine.add(row4);
        rowsLine.add(row5);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return execute.getMessageId();
    }
    private void checkPhoneFormat(UserState thisState, String phone) {
        if (phone.startsWith("7") || (phone.startsWith("8") || (phone.startsWith("+7")))
                && ((phone.length() == 11) || (phone.length() == 12))) {
            Order order = new Order();
            order.setId(thisState.getChatId());
            order.setPhoneNumber(phone);
            order.setFinished(false);
            orderRepository.save(order);
            thisState.setAddPhone(false);
            thisState.setAskAddress(true);
            int userMesId = sendMessageWithPickup(thisState.getChatId(), "Номер телефона успешно добавлен, введите адрес" +
                    "доставки или нажмите \"Самовывоз\": ",
                    thisState.getMessageId());
            thisState.setMessageId(userMesId);
            userStateRepository.save(thisState);
        } else
            sendMessage(thisState.getChatId(), "Номер телефона должен начинаться с 7,8, или +7" +
                    ", с содержать 11-12 символов. Введите еще раз");
    }
    private void changePhone(UserState thisState, String phone) {
        if (phone.startsWith("7") || (phone.startsWith("8") || (phone.startsWith("+7")))
                && ((phone.length() == 11) || (phone.length() == 12))) {
            Order order = getOrderById(thisState.getChatId());
            order.setPhoneNumber(phone);
            orderRepository.save(order);
            thisState.setAddPhone(false);
            thisState.setChanged(false);
            thisState.setAcceptOrder(true);
            sendMessage(thisState.getChatId(),
                    "Номер телефона успешно изменен");
            int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                            " оформить заказ, или измените необходимые параметры заказа.",
                    thisState.getMessageId());
            thisState.setMessageId(userMesId);
            userStateRepository.save(thisState);
        } else
            sendMessage(thisState.getChatId(), "Номер телефона должен начинаться с 7,8, или +7" +
                    ", с содержать 11-12 символов. Введите еще раз");
    }
    private void checkTimeFormat(UserState thisState, String time) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("kk:mm");
        try {
            System.out.println(dateFormat.parse(time));
            Order order = getOrderById(thisState.getChatId());
            order.setTime(time);
            orderRepository.save(order);
            thisState.setSelectTime(false);
            thisState.setAcceptOrder(true);
            int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                    " оформить заказ, или измените необходимые параметры заказа.",
                    thisState.getMessageId());
            thisState.setMessageId(userMesId);
            userStateRepository.save(thisState);
        } catch (ParseException e) {
            sendMessage(thisState.getChatId(), "Введен неверный формат времени," +
                    " попробуйте еще раз.");
            throw new RuntimeException(e);
        }
    }
    private void changeTime (UserState thisState, String time) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("kk:mm");
        try {
            System.out.println(dateFormat.parse(time));
            Order order = getOrderById(thisState.getChatId());
            order.setTime(time);
            orderRepository.save(order);
            thisState.setSelectTime(false);
            thisState.setChanged(false);
            int userMesId = sendMessageWithAcceptOrder(thisState.getChatId(), "Проверьте данные и нажмите подтвердить, чтобы" +
                            " оформить заказ, или измените необходимые параметры заказа.",
                    thisState.getMessageId());
            thisState.setMessageId(userMesId);
            userStateRepository.save(thisState);
        } catch (ParseException e) {
            sendMessage(thisState.getChatId(), "Введен неверный формат времени," +
                    " попробуйте еще раз.");
            throw new RuntimeException(e);
        }
    }
    private void sendMessageAboutOrderToChat(Bucket bucket, Order order){
        String goodString = bucket.getGoodsId();
        StringBuilder builder = new StringBuilder("Заказ: \n");
        String[] split = goodString.split("/");
        List<String> goodList = new ArrayList<>();
        for (String str:split) {
            goodList.add(str);
        }
        HashSet<String> set = new HashSet<>(goodList);
        for (String goodId:set) {
            Good good = getGoodById(Long.valueOf(goodId));
            int count = 0;
            for (int i = 0; i < goodList.size(); i++) {
                if (goodId.equals(goodList.get(i))) {
                    count++;
                }
            }
            builder.append("\n" + good.getName() + " - " + count + "шт.\n");
        }
        String finalList = String.valueOf(builder);
        SendMessage message = new SendMessage();
        message.setChatId(goupId);
        message.setText(finalList + "\nСтоимость: " + bucket.getFullPrice() +
                "\nАдрес: " + order.getAddress() + "\nТелефон: " + order.getPhoneNumber() +
                "\nВремя доставки: " + order.getTime() + "\nПожелания: " + order.getAddition());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .callbackData("Complete")
                .text("Завершить заказ")
                .build();
        row.add(inlineKeyboardButton);
        rowsLine.add(row);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void showMenu(UserState state, Cloud cloud) {
        state.setShowMenu(true);
        List<Category> categories = getCategories();
//        String[] categories = cloud.getCategories().split("/");
//        ArrayList<String> list = new ArrayList<>();
//        for (String goodCat:categories) {
//            list.add(goodCat);
//        }
        int userMesId = printCategories(state, categories);
        state.setMessageId(userMesId);
        userStateRepository.save(state);
    }
    private void showCategories(UserState state, long messageId, String categoryId) {
        state.setShowMenu(false);
        state.setShowCategory(true);
        List<Good> goodList = getAllGoodsFromCategory(categoryId);
        String goodIdList = getAllGoodsIdFromCategory(categoryId);
        int userMesId = printGoodFromCategoryId(state, goodIdList, messageId, 0);
        state.setMessageId(userMesId);
        userStateRepository.save(state);
    }
    private int printCategories(UserState state, List<Category> list){
        SendPhoto photo = new SendPhoto();
        SendMessage message = new SendMessage();
        photo.setChatId(state.getChatId());
        photo.setCaption("\uD83D\uDC4B Вас приветствует доставка еды сети ресторанов" +
                        "\n\uD83D\uDD25МИЯГИ FAMILY\uD83D\uDD25" + "\n\uD83D\uDC47 Выберите категорию:");
        photo.setPhoto(new InputFile(new File("C:/photos/logo.jpg")));
        message.setText("Выберите категорию:");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton inlineKeyboardButtonDriver = InlineKeyboardButton.builder()
                    .callbackData("Category/" + String.valueOf(list.get(i).getId()))
                    .text(list.get(i).getName())
                    .build();
            row.add(inlineKeyboardButtonDriver);
            rowsLine.add(row);
        }
        markup.setKeyboard(rowsLine);
        photo.setReplyMarkup(markup);
        Message execute;
        try {
            execute = execute(photo);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return execute.getMessageId();
    }
    private int printGoodFromCategoryId(UserState state, String goodsId, long messageId, int i) {
        Bucket bucket = getBucketById(state.getChatId());
        SendPhoto photo = new SendPhoto();
        String[] idsArray = goodsId.split("/");
        Good good = getGoodById(Long.valueOf(idsArray[i]));
        Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
        photo.setChatId(state.getChatId());
        if (category.isAdditionalPar()) {
            photo.setCaption(good.getName() + "\n" + good.getInfo() + "\nЦена: " + good.getAdditionalPars());
        } else {
            photo.setCaption(good.getName() + "\n" + good.getInfo() + "\nЦена: " + good.getPrice());
        }
        photo.setPhoto(new InputFile(new File("C:/photos/" + good.getId() + ".png")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        List<InlineKeyboardButton> rowTwo = new ArrayList<>();
        List<InlineKeyboardButton> rowThree = new ArrayList<>();
//        if (category.isAdditionalPar()) {
//            List<InlineKeyboardButton> rowSec = new ArrayList<>();
//            if (bucket.getId() != 1L) {
//                String[] str = bucket.getGoodsId().split("/");
//                int count = str.length;
//                InlineKeyboardButton inlineKeyboardButtonBucket = InlineKeyboardButton.builder()
//                        .callbackData("GoBucket")
//                        .text("\uD83D\uDC49 К заказу (" + count + ")")
//                        .build();
//                rowSec.add(inlineKeyboardButtonBucket);
//                rowsLine.add(rowSec);
//            }
//            int varsCount = Integer.parseInt(category.getVarsCount());
//            int res = 0;
//            for (int j = 0; j < varsCount/2; j++) {
//                List<InlineKeyboardButton> newRow = new ArrayList<>();
//                for (int k = 0; k < 2; k++) {
//                    InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
//                            .callbackData("InBucket:" + good.getId() + ":" + goodsId + ":" + good.getParametr(res))
//                            .text("\uD83D\uDED2 " + category.getParametr(res) + " (" + good.getParametr(res) + " р.)")
//                            .build();
//                    res = res + 1;
//                    newRow.add(inlineKeyboardButton);
//                }
//                rowsLine.add(newRow);
//            }
//            if (varsCount % 2 != 0) {
//                List<InlineKeyboardButton> newRow = new ArrayList<>();
//                InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
//                        .callbackData("InBucket:" + good.getId() + ":" + goodsId + ":" + good.getParametr(res))
//                        .text("\uD83D\uDED2 " + category.getParametr(res) + " (" + good.getParametr(res) + " р.)")
//                        .build();
//                newRow.add(inlineKeyboardButton);
//                rowsLine.add(newRow);
//            }
//        } else {
            InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                    .callbackData("InBucket:" + good.getId() + ":" + goodsId)
                    .text("\uD83D\uDED2 В корзину")
                    .build();
            row.add(inlineKeyboardButton);
            if (bucket.getId() != 1L) {
                String[] str = bucket.getGoodsId().split("/");
                int count = str.length;
                InlineKeyboardButton inlineKeyboardButtonBucket = InlineKeyboardButton.builder()
                        .callbackData("GoBucket")
                        .text("\uD83D\uDC49 К заказу (" + count + ")")
                        .build();
                row.add(inlineKeyboardButtonBucket);
//            }
        }
        InlineKeyboardButton inlineKeyboardButton1 = InlineKeyboardButton.builder()
                .callbackData("Order/" + good.getId())
                .text("✅ Заказать сейчас")
                .build();
        InlineKeyboardButton inlineKeyboardButton2 = InlineKeyboardButton.builder()
                .callbackData("Right:" + idsArray[i] + ":" + goodsId)
                .text("➡️")
                .build();
        InlineKeyboardButton inlineKeyboardButton3 = InlineKeyboardButton.builder()
                .callbackData("Menu:")
                .text("\uD83D\uDCCB" + " Меню")
                .build();
        InlineKeyboardButton inlineKeyboardButton4 = InlineKeyboardButton.builder()
                .callbackData("Left:" + idsArray[i] + ":" + goodsId)
                .text("⬅️")
                .build();
        rowTwo.add(inlineKeyboardButton1);
        rowThree.add(inlineKeyboardButton4);
        rowThree.add(inlineKeyboardButton3);
        rowThree.add(inlineKeyboardButton2);
        rowsLine.add(row);
        rowsLine.add(rowTwo);
        rowsLine.add(rowThree);
        markup.setKeyboard(rowsLine);
        photo.setReplyMarkup(markup);
        deleteMessage(state.getChatId(),(int) messageId);
        Message execute;
        try {
            execute = execute(photo);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return execute.getMessageId();
    }
    private void createBucket(UserState state, Long goodId) {
        Bucket bucket = new Bucket();
        Good good = getGoodById(goodId);
        if (bucketRepository.findById(state.getChatId()).isEmpty()) {
            bucket.setId(state.getChatId());
            bucket.setGoodsId(goodId + "/");
            bucket.setAdditionalGoods("");
            bucket.setFullPrice(good.getPrice());
            bucketRepository.save(bucket);
        } else {
            Optional<Bucket> optBucket = bucketRepository.findById(state.getChatId());
            if (optBucket.isPresent())
                bucket = optBucket.get();
            bucket.setGoodsId(bucket.getGoodsId() + good.getId() + "/");
            bucket.setFullPrice(bucket.getFullPrice() + good.getPrice());
            bucketRepository.save(bucket);
        }
    }
    private void createBucketWitchPar(UserState state, Long goodId, Float price) {
        System.out.println(price);
        Bucket bucket = new Bucket();
        Good good = getGoodById(goodId);
        if (bucketRepository.findById(state.getChatId()).isEmpty()) {
            bucket.setId(state.getChatId());
            bucket.setAdditionalGoods(goodId + ":" + price + "/");
            bucket.setGoodsId(goodId + "/");
            bucket.setFullPrice(price);
            bucketRepository.save(bucket);
        } else {
            Optional<Bucket> optBucket = bucketRepository.findById(state.getChatId());
            if (optBucket.isPresent())
                bucket = optBucket.get();
            bucket.setAdditionalGoods(bucket.getAdditionalGoods() + goodId + ":" + price + "/");
            bucket.setGoodsId(bucket.getGoodsId() + good.getId() + "/");
            bucket.setFullPrice(bucket.getFullPrice() + price);
            bucketRepository.save(bucket);
        }
    }
    private Bucket getBucketById(Long id) {
        Bucket bucket = new Bucket();
        bucket.setId(1L);
        Optional<Bucket> optBucket = bucketRepository.findById(id);
        if (optBucket.isPresent())
            bucket = optBucket.get();
        return bucket;
    }
    private Order getOrderById(Long id) {
        Order order = new Order();
        Optional<Order> optOrder = orderRepository.findById(id);
        if (optOrder.isPresent())
            order = optOrder.get();
        return order;
    }
    private void showBucket(UserState state, long messageId) {
        if (bucketRepository.existsById(state.getChatId())) {
            if (getBucketById(state.getChatId()).getGoodsId().equals("")) {
                deleteMessage(state.getChatId(), state.getMessageId());
                sendMessage(state.getChatId(), "Ваша корзина пуста");
                Bucket bucket = getBucketById(state.getChatId());
                if (bucket.getId() != 1L) {
                    bucketRepository.deleteById(state.getChatId());
                }
            } else if (getBucketById(state.getChatId()).getId() != 1L) {
                SendMessage message = new SendMessage();
                message.setChatId(state.getChatId());
                StringBuilder builder = new StringBuilder("Ваша корзина: \n");
                String goodString = getBucketById(state.getChatId()).getGoodsId();
                String[] split = goodString.split("/");
                String[] splitTwo = getBucketById(state.getChatId()).getAdditionalGoods().split("/");
                List<String> goodList = new ArrayList<>();
                for (String str : split) {
                    goodList.add(str);
                }
                HashSet<String> set = new HashSet<>(goodList);
                for (String goodId : set) {
                    Good good = getGoodById(Long.valueOf(goodId));
                    System.out.println(good.getCategory());
                    Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
                    if (!category.isAdditionalPar()) {
                        int count = 0;
                        for (int i = 0; i < goodList.size(); i++) {
                            if (goodId.equals(goodList.get(i))) {
                                count++;
                            }
                        }
                        builder.append("\n" + good.getName() + " - " + count + "шт.\n");
                    }
                }
                System.out.println(splitTwo + " splitTwo");
                ArrayList<String> additionList = new ArrayList<>(List.of(splitTwo));
                HashSet<String> additionSet = new HashSet<>(additionList);
                System.out.println(additionList.isEmpty());
                System.out.println(additionList.get(0).equals(""));
                if (!additionList.get(0).equals("")) {
                    for (String goodIdAndPrice : additionSet) {
                        String goodId = goodIdAndPrice.split(":")[0];
                        System.out.println(goodId);
                        StringBuilder priceBuilder = new StringBuilder(goodIdAndPrice.split(":")[1]);
                        priceBuilder.setLength(priceBuilder.length() - 2);
                        String price = String.valueOf(priceBuilder);
                        Good good = getGoodById(Long.valueOf(goodId));
                        Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
                        int parNumber = good.getParNumber(price);
                        String additionPar = category.getParametr(parNumber);

                        int count = 0;
                        for (int i = 0; i < additionList.size(); i++) {
                            if (goodId.equals(additionList.get(i).split(":")[0])
                                    && goodIdAndPrice.split(":")[1].equals(additionList.get(i).split(":")[1])) {
                                count++;
                            }
                        }
                        builder.append("\n" + good.getName() + " " + additionPar + " - " + count + "шт.\n");
                    }
                }
                builder.append("\nСтоимость заказа: " + getBucketById(state.getChatId()).getFullPrice() + "\n");
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                for (String str : set) {
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    if (!getCategoryFromId(Long.valueOf(getGoodById(Long.valueOf(str)).getCategory()))
                            .isAdditionalPar()) {
                        StringBuilder builder1 = new StringBuilder(String.valueOf(getGoodById(Long.valueOf(str)).getPrice()));
                        builder1.setLength(builder1.length() - 2);
                        builder1.append(" р.");
                        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                                .callbackData("DeleteGood/" + str)
                                .text("❌ " + getGoodById(Long.valueOf(str)).getName() +
                                        " (" + String.valueOf(builder1) + ") 1 шт.")
                                .build();
                        row.add(inlineKeyboardButton);
                        rowsLine.add(row);
                    }
                }
                if (!additionList.get(0).equals("")) {
                    for (String str : additionSet) {
                        String[] result = str.split(":");
                        String id = result[0];
                        Good good = getGoodById(Long.valueOf(id));
                        Category category = getCategoryFromId(Long.valueOf(good.getCategory()));
                        StringBuilder builder1 = new StringBuilder(result[1]);
                        builder1.setLength(builder1.length() - 2);
                        String price = String.valueOf(builder1);
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                                .callbackData("DeleteGood/" + id + "/" + price +".0")
                                .text("❌ " + good.getName()
                                        + " " + category.getParametr(good.getParNumber(price))
                                        + " (" + price + " р.) 1 шт.")
                                .build();
                        row.add(inlineKeyboardButton);
                        rowsLine.add(row);
                    }
            }
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                        .callbackData("Accept")
                        .text("Оформить заказ")
                        .build();
                row.add(inlineKeyboardButton);
                rowsLine.add(row);
                markup.setKeyboard(rowsLine);
                message.setReplyMarkup(markup);
                message.setText(String.valueOf(builder));
                deleteMessage(state.getChatId(), state.getMessageId());
                Message execute;
                try {
                    execute = execute(message);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                state.setMessageId(execute.getMessageId());
                userStateRepository.save(state);
//        sendMessageWithAccept(state.getChatId());
            } else {
                deleteMessage(state.getChatId(), state.getMessageId());
                sendMessage(state.getChatId(), "Ваша корзина пуста");
                state.setMessageId(0);
                userStateRepository.save(state);
            }
        } else {
            deleteMessage(state.getChatId(), state.getMessageId());
            sendMessage(state.getChatId(), "Ваша корзина пуста");
            state.setMessageId(0);
            userStateRepository.save(state);
        }
    }
    private Good getGoodById(Long id) {
        Good good = new Good();
        Optional<Good> optGood = goodRepository.findById(id);
        if (optGood.isPresent())
            good = optGood.get();
        return good;
    }
    private void executeMessage(SendMessage message) {
        try{
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }
    private void deleteMessage(Long chatId, int messageId) {
        if (messageId != 0) {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId);
            deleteMessage.setMessageId(messageId);
            try {
                execute(deleteMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            } catch (BadRequestException s) {
                s.printStackTrace();
            }
        } else System.out.println("sorry(((");
    }
    private void breakState(UserState state) {
        state.setShowBucket(false);
        state.setAcceptOrder(false);
        state.setShowMenu(false);
        state.setAskAddition(false);
        state.setAskAddress(false);
        state.setAddPhone(false);
        state.setShowCategory(false);
        state.setSelectTime(false);
        state.setChanged(false);
        userStateRepository.save(state);
    }
    private boolean checkUserIsAdmin(long chatId, Cloud cloud) {
        List<String> admins = cloud.getAdminIds();
        if (admins.contains(String.valueOf(chatId))) {
            return true;
        } else return false;
    }
    private UserState checkUserState(Update update) {
        Long chatId = 0L;
        try {
            if (update.hasCallbackQuery())
                chatId = update.getCallbackQuery().getMessage().getChatId();
            else if (update.hasMessage() && update.getMessage().hasText())
                chatId = update.getMessage().getChatId();
            else chatId = update.getMessage().getChatId();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        UserState userState = new UserState();
        if (userStateRepository.findById(chatId).isEmpty()) {
            userState.setChatId(chatId);
            userStateRepository.save(userState);
        } else {
            Optional<UserState> optState = userStateRepository.findById(chatId);
            if (optState.isPresent())
                userState = optState.get();
        }
        return userState;
    }
    private Cloud getCloud() {
        Cloud cloud = new Cloud();
        if (cloudRepository.findById(1L).isEmpty()) {
            cloud.setId(1L);
            cloudRepository.save(cloud);
        } else {
            Optional<Cloud> optionalCloud = cloudRepository.findById(1L);
            if (optionalCloud.isPresent())
                cloud = optionalCloud.get();
        }
        return cloud;
    }
    private Category getCategoryFromId(Long id) {
        Category category = new Category();
        if (categoryRepository.findById(id).isEmpty()) {
            System.out.println("нет категории");
        } else {
            Optional<Category> optionalCloud = categoryRepository.findById(id);
            if (optionalCloud.isPresent())
                category = optionalCloud.get();
        }
        return category;
    }
    private List<Good> getAllGoodsFromCategory(String category) {
        Iterable<Good> goods = goodRepository.findAll();
        List<Good> goodList = new ArrayList<>();
        for (Good good: goods) {
            if (category.equals(good.getCategory()))
                goodList.add(good);
        }
            return goodList;
    }
    private String getAllGoodsIdFromCategory(String category) {
        Iterable<Good> goods = goodRepository.findAll();
        StringBuilder builder = new StringBuilder();
        for (Good good: goods) {
            if (category.equals(good.getCategory()))
                builder.append(good.getId() + "/");
        }
        String result = String.valueOf(builder);
        return result;
    }
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }
    public String getBotToken() {
        return config.getToken();
    }

}
