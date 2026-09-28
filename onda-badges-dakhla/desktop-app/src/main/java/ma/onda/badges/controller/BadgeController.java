package ma.onda.badges.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ma.onda.badges.model.Badge;
import ma.onda.badges.service.ExcelParserService;
import ma.onda.badges.service.NotificationService;

/**
 * Controller managing badge data parsing, filtering, and reporting.
 */
public class BadgeController {

    private final ExcelParserService excelParserService;
    private final NotificationService notificationService;

    public BadgeController() {
        this(new NotificationService());
    }

    public BadgeController(NotificationService notificationService) {
        this.excelParserService = new ExcelParserService();
        this.notificationService = notificationService != null ? notificationService : new NotificationService();
    }

    public List<Badge> loadBadgesFromExcel(String filePath) throws Exception {
        List<Badge> badges = excelParserService.parseExcelFile(filePath);
        notificationService.notifyExpiredBadges(badges);
        return badges;
    }

    public List<Badge> filterBadges(List<Badge> masterList, String filterType, int thresholdDays) {
        if (masterList == null || masterList.isEmpty()) {
            return new ArrayList<>();
        }

        if ("RELANCE".equalsIgnoreCase(filterType)) {
            return masterList.stream()
                    .filter(b -> {
                        Long days = b.getDaysRemaining();
                        return days != null && days >= 0 && days <= thresholdDays;
                    })
                    .collect(Collectors.toList());
        } else if ("EXPIRED".equalsIgnoreCase(filterType)) {
            return masterList.stream()
                    .filter(b -> {
                        Long days = b.getDaysRemaining();
                        return days != null && days < 0;
                    })
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>(masterList);
        }
    }



    public ExcelParserService getExcelParserService() {
        return excelParserService;
    }
}
