package ch.puzzle.ln.pos.web.rest;

import ch.puzzle.ln.pos.service.ShopService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
public class ShopResource {

    private final ShopService shopService;

    public ShopResource(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/available")
    public HttpEntity<Object> shopAvailable() {
        if (shopService.isDisabled() || shopService.isPublicHoliday() || shopService.isOutsideOpeningHours()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new HttpEntity<>(shopService.getDelayMinutes());
    }
}
