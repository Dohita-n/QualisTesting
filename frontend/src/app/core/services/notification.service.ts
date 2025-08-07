import { Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';

export type NotificationType = 'success' | 'info' | 'warning' | 'error';

export interface NotificationOptions {
  life?: number;
  closable?: boolean;
  sticky?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  
  constructor(private messageService: MessageService) {}

  /**
   * Affiche un toast de succès
   */
  showSuccess(message: string, title: string = 'Succès', options: NotificationOptions = {}): void {
    this.showNotification('success', title, message, options);
  }

  /**
   * Affiche un toast d'information
   */
  showInfo(message: string, title: string = 'Information', options: NotificationOptions = {}): void {
    this.showNotification('info', title, message, options);
  }

  /**
   * Affiche un toast d'avertissement
   */
  showWarning(message: string, title: string = 'Avertissement', options: NotificationOptions = {}): void {
    this.showNotification('warning', title, message, options);
  }

  /**
   * Affiche un toast d'erreur
   */
  showError(message: string, title: string = 'Erreur', options: NotificationOptions = {}): void {
    this.showNotification('error', title, message, options);
  }

  /**
   * Méthode générique pour afficher une notification
   */
  private showNotification(
    severity: NotificationType, 
    summary: string, 
    detail: string, 
    options: NotificationOptions = {}
  ): void {
    const defaultOptions: NotificationOptions = {
      life: 4000,
      closable: true,
      sticky: false
    };

    const finalOptions = { ...defaultOptions, ...options };

    this.messageService.add({
      severity,
      summary,
      detail,
      life: finalOptions.life,
      closable: finalOptions.closable,
      sticky: finalOptions.sticky
    });
  }

  /**
   * Efface toutes les notifications
   */
  clear(): void {
    this.messageService.clear();
  }

  /**
   * Efface les notifications d'un type spécifique
   */
  clearByType(severity: NotificationType): void {
    this.messageService.clear(severity);
  }
}
