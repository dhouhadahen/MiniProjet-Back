package pharmacie.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Imports de SendGrid
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Fournisseur;
import pharmacie.entity.Medicament;

@Service
public class ReapprovisionnementService {

    private final MedicamentRepository medicamentRepository;

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    public ReapprovisionnementService(MedicamentRepository medicamentRepository) {
        this.medicamentRepository = medicamentRepository;
    }

    public String processReapprovisionnement() {

        List<Medicament> tousMedicaments = medicamentRepository.findAll();
        List<Medicament> aReappro = new ArrayList<>();
        
        // 1. On cherche les médicaments en rupture de stock
        for (Medicament m : tousMedicaments) {
            if (m.getUnitesEnStock() < m.getNiveauDeReappro()) {
                aReappro.add(m);
            }
        }

        if (aReappro.isEmpty()) {
            return "Aucun médicament à réapprovisionner.";
        }

        // 2. On groupe les médicaments par fournisseur
        Map<Fournisseur, List<Medicament>> parFournisseur = new HashMap<>();

        for (Medicament m : aReappro) {
            Set<Fournisseur> fournisseurs = m.getCategorie().getFournisseurs();

            if (fournisseurs == null || fournisseurs.isEmpty()) continue;

            for (Fournisseur f : fournisseurs) {
                if (!parFournisseur.containsKey(f)) {
                    parFournisseur.put(f, new ArrayList<>());
                }
                parFournisseur.get(f).add(m);
            }
        }

        int nbMails = 0;
        StringBuilder resultat = new StringBuilder();

        // 3. Préparation et envoi des mails
        for (Map.Entry<Fournisseur, List<Medicament>> entry : parFournisseur.entrySet()) {
            Fournisseur fournisseur = entry.getKey();
            List<Medicament> medicaments = entry.getValue();

            // -- NOUVEAUTÉ : On regroupe les médicaments par CATÉGORIE pour ce fournisseur
            Map<String, List<Medicament>> medocsParCategorie = new HashMap<>();
            for (Medicament m : medicaments) {
                String nomCat = m.getCategorie().getLibelle();
                if (!medocsParCategorie.containsKey(nomCat)) {
                    medocsParCategorie.put(nomCat, new ArrayList<>());
                }
                medocsParCategorie.get(nomCat).add(m);
            }

            // -- Construction du contenu du mail
            StringBuilder contenuMail = new StringBuilder();
            contenuMail.append("Bonjour ").append(fournisseur.getNom()).append(",\n\n");
            contenuMail.append("Veuillez nous transmettre un devis pour le réapprovisionnement des produits suivants :\n\n");

            // On parcourt les catégories
            for (Map.Entry<String, List<Medicament>> catEntry : medocsParCategorie.entrySet()) {
                contenuMail.append("📦 CATÉGORIE : ").append(catEntry.getKey()).append("\n");
                // On parcourt les médicaments de cette catégorie
                for (Medicament m : catEntry.getValue()) {
                    contenuMail.append("   - ").append(m.getNom())
                               .append(" [Réf: ").append(m.getReference()).append("]\n");
                }
                contenuMail.append("\n");
            }
            
            contenuMail.append("Cordialement,\nLa Pharmacie\n");

            String sujet = "Demande de devis - Réapprovisionnement";

            // =================================================================
            // AFFICHAGE AMÉLIORÉ DANS LE TERMINAL
            // =================================================================
            System.out.println("\n======================================================================");
            System.out.println("📧 NOUVEL E-MAIL ENVOYÉ (SIMULATION & ENVOI RÉEL)");
            System.out.println("======================================================================");
            System.out.println("À      : " + fournisseur.getEmail());
            System.out.println("OBJET  : " + sujet);
            System.out.println("----------------------------------------------------------------------");
            System.out.print(contenuMail.toString());
            System.out.println("======================================================================");
            // =================================================================

            // -- Envoi via SendGrid
            try {
                Email from = new Email(fromEmail);
                Email to = new Email(fournisseur.getEmail());
                Content content = new Content("text/plain", contenuMail.toString());
                Mail mail = new Mail(from, sujet, to, content);

                SendGrid sg = new SendGrid(sendGridApiKey);
                Request request = new Request();
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());
                
                Response response = sg.api(request);
                
                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    nbMails++;
                    System.out.println("✅ Statut API SendGrid : SUCCÈS (" + response.getStatusCode() + ")\n");
                } else {
                    System.out.println("❌ Erreur SendGrid : " + response.getBody() + "\n");
                }
            } catch (Exception e) {
                System.out.println("❌ Exception d'envoi : " + e.getMessage() + "\n");
            }
            
            resultat.append("Mail traité pour ").append(fournisseur.getNom()).append("\n");
        }

        return resultat.toString() + "\nTotal mails envoyés : " + nbMails;
    }
}