package pharmacie.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pharmacie.service.ReapprovisionnementService;

@RestController
public class ReapprovisionnementController {

    private final ReapprovisionnementService reapproService;

    public ReapprovisionnementController(ReapprovisionnementService reapproService) {
        this.reapproService = reapproService;
    }

    @GetMapping("/api/reapprovisionnement")
    public String lancerReapprovisionnement() {
        System.out.println("=== Appel de /api/reapprovisionnement ===");
        String resultat = reapproService.processReapprovisionnement();
        return resultat;
    }
}
