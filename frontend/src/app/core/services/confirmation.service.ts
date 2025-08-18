import { Injectable } from '@angular/core';
import { ConfirmationService } from 'primeng/api';

export interface ConfirmationOptions {
  message?: string;
  header?: string;
  icon?: string;
  acceptLabel?: string;
  rejectLabel?: string;
  acceptIcon?: string;
  rejectIcon?: string;
  acceptButtonStyleClass?: string;
  rejectButtonStyleClass?: string;
}

@Injectable({
  providedIn: 'root'
})
export class CustomConfirmationService {
  
  constructor(private confirmationService: ConfirmationService) {}

  /**
   * Affiche une boîte de dialogue de confirmation
   */
  confirm(options: ConfirmationOptions): Promise<boolean> {
    return new Promise((resolve) => {
      this.confirmationService.confirm({
        message: options.message || 'Êtes-vous sûr de vouloir continuer ?',
        header: options.header || 'Confirmation',
        icon: options.icon || 'pi pi-exclamation-triangle',
        acceptLabel: options.acceptLabel || 'Oui',
        rejectLabel: options.rejectLabel || 'Non',
        acceptIcon: options.acceptIcon || 'pi pi-check',
        rejectIcon: options.rejectIcon || 'pi pi-times',
        acceptButtonStyleClass: options.acceptButtonStyleClass || 'p-button-danger',
        rejectButtonStyleClass: options.rejectButtonStyleClass || 'p-button-secondary',
        closable: true,
        closeOnEscape: true,
        dismissableMask: true,
        blockScroll: true,
        accept: () => {
          try { this.confirmationService.close(); } catch {}
          resolve(true);
        },
        reject: () => {
          try { this.confirmationService.close(); } catch {}
          resolve(false);
        }
      });
    });
  }

  /**
   * Affiche une confirmation de suppression
   */
  confirmDelete(itemName: string): Promise<boolean> {
    return this.confirm({
      message: `Êtes-vous sûr de vouloir supprimer "${itemName}" ? Cette action ne peut pas être annulée.`,
      header: 'Confirmation de suppression',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Supprimer',
      rejectLabel: 'Annuler',
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary'
    });
  }

  /**
   * Affiche une confirmation d'action critique
   */
  confirmCriticalAction(action: string, itemName: string): Promise<boolean> {
    return this.confirm({
      message: `Êtes-vous sûr de vouloir ${action} "${itemName}" ? Cette action peut avoir des conséquences importantes.`,
      header: 'Action critique',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Continuer',
      rejectLabel: 'Annuler',
      acceptButtonStyleClass: 'p-button-warning',
      rejectButtonStyleClass: 'p-button-secondary'
    });
  }
}
