package pharmacie.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Fournisseur;
import pharmacie.entity.Medicament;

@Service
public class ReapprovisionnementService {

    private final MedicamentRepository medicamentRepository;

    private static final String NOM_ETUDIANT = "jonniaux.math";

    // Config Mailgun récupérée depuis application.properties
    @Value("${mailgun.api-key}")
    private String mailgunApiKey;

    @Value("${mailgun.domain}")
    private String mailgunDomain;

    @Value("${mailgun.from}")
    private String mailgunFrom;

    public ReapprovisionnementService(MedicamentRepository medicamentRepository) {
        this.medicamentRepository = medicamentRepository;
    }

    public String processReapprovisionnement() {

        List<Medicament> tousMedicaments = medicamentRepository.findAll();

        // On filtre les médicaments dont le stock est en dessous du seuil de réappro
        List<Medicament> aReappro = new ArrayList<>();
        for (Medicament m : tousMedicaments) {
            if (m.getNiveauDeReappro() != null && m.getUnitesEnStock() != null) {
                if (m.getUnitesEnStock() < m.getNiveauDeReappro()) {
                    aReappro.add(m);
                    System.out.println("Stock faible pour : " + m.getNom()
                            + " (stock=" + m.getUnitesEnStock() + ", seuil=" + m.getNiveauDeReappro() + ")");
                }
            }
        }

        if (aReappro.isEmpty()) {
            System.out.println("Aucun médicament à réapprovisionner !");
            return "Aucun médicament à réapprovisionner.";
        }

        // On groupe par fournisseur pour envoyer un seul mail par fournisseur
        // On passe par la catégorie du médicament pour trouver ses fournisseurs
        Map<Fournisseur, List<Medicament>> parFournisseur = new HashMap<>();

        for (Medicament m : aReappro) {
            Set<Fournisseur> fournisseurs = m.getCategorie().getFournisseurs();

            if (fournisseurs == null || fournisseurs.isEmpty()) {
                System.out.println("Pas de fournisseur pour le médicament : " + m.getNom());
                continue;
            }

            for (Fournisseur f : fournisseurs) {
                if (!parFournisseur.containsKey(f)) {
                    parFournisseur.put(f, new ArrayList<>());
                }
                parFournisseur.get(f).add(m);
            }
        }

        int nbMails = 0;
        StringBuilder resultat = new StringBuilder();

        for (Map.Entry<Fournisseur, List<Medicament>> entry : parFournisseur.entrySet()) {
            Fournisseur fournisseur = entry.getKey();
            List<Medicament> medicaments = entry.getValue();

            // En sandbox Mailgun, on envoie tout à notre seule adresse autorisée
            String emailDest = NOM_ETUDIANT + "@gmail.com";

            StringBuilder contenuMail = new StringBuilder();
            contenuMail.append("Bonjour ").append(fournisseur.getNom()).append(",\n\n");
            contenuMail.append("Nous avons besoin de réapprovisionner les médicaments suivants :\n\n");

            for (Medicament m : medicaments) {
                contenuMail.append("- ").append(m.getNom())
                        .append(" (stock actuel : ").append(m.getUnitesEnStock())
                        .append(", seuil : ").append(m.getNiveauDeReappro())
                        .append(")\n");
            }

            contenuMail.append("\nMerci de nous livrer rapidement.\n");
            contenuMail.append("Cordialement,\nPharmacie");

            String sujet = "Demande de réapprovisionnement - " + fournisseur.getNom();

            try {
                envoyerMailMailgun(emailDest, sujet, contenuMail.toString());
                System.out.println("Mail envoyé via Mailgun à : " + emailDest);
                nbMails++;

            } catch (Exception e) {
                // Fallback si Mailgun ne marche pas : on affiche le mail en console
                System.out.println("=== ERREUR MAILGUN : " + e.getMessage() + " ===");
                System.out.println("=== SIMULATION MAIL ===");
                System.out.println("Destinataire : " + emailDest);
                System.out.println("Sujet : " + sujet);
                System.out.println("Contenu :");
                System.out.println(contenuMail.toString());
                System.out.println("=== FIN MAIL ===");
                nbMails++;
            }

            resultat.append("Mail envoyé/simulé pour ").append(fournisseur.getNom())
                    .append(" (").append(medicaments.size()).append(" médicaments)\n");
        }

        String message = "Réapprovisionnement terminé. " + nbMails + " mail(s) envoyé(s)/simulé(s).";
        System.out.println(message);

        return resultat.toString() + "\n" + message;
    }

    // Envoie un mail via l'API HTTP de Mailgun
    private void envoyerMailMailgun(String to, String subject, String text) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("api", mailgunApiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("from", mailgunFrom);
        body.add("to", to);
        body.add("subject", subject);
        body.add("text", text);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        String url = "https://api.mailgun.net/v3/" + mailgunDomain + "/messages";
        String response = restTemplate.postForEntity(url, request, String.class).getBody();
        System.out.println("Réponse Mailgun : " + response);
    }
}
