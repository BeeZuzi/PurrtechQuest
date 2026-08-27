package eu.purrtech.purrtechQuest.tracking;

import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.service.QuestService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import su.nightexpress.excellentshop.api.event.TransactionCompletedEvent;
import su.nightexpress.excellentshop.api.product.TradeType;
import su.nightexpress.excellentshop.api.transaction.ECompletedTransaction;

/**
 * SPEND_MONEY tracks money spent at an ExcellentShop NPC/GUI shop — only registered when ExcellentShop is
 * installed (see {@code PurrtechQuest.registerSoftDependListeners}). {@code worth()} is a
 * {@code BalanceHolder}, i.e. a per-currency balance map (ExcellentShop supports multiple currencies at
 * once) — summed across every currency in the transaction rather than picking one, since a quest objective
 * just wants "how much did they spend," not which currency it was in. Fractional totals are floored: quest
 * progress amounts are whole numbers, and a shop transaction under 1 unit of currency is rare enough that
 * losing the remainder isn't worth tracking sub-unit progress for.
 */
public final class ExcellentShopSpendListener implements Listener {

    private final QuestService questService;

    public ExcellentShopSpendListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        ECompletedTransaction transaction = event.getTransaction();
        if (transaction.type() != TradeType.BUY || !transaction.successful()) {
            return;
        }
        double spent = transaction.worth().getBalanceMap().values().stream().mapToDouble(Double::doubleValue).sum();
        int amount = (int) Math.floor(spent);
        if (amount <= 0) {
            return;
        }
        questService.updateProgress(transaction.player(), ObjectiveType.SPEND_MONEY, ObjectiveType.SPEND_MONEY_TARGET, amount);
    }
}
