import { Component, HostListener, OnDestroy } from '@angular/core';
import { Invoice } from 'app/shared/model/invoice.model';
import { CheckoutService } from 'app/shop/checkout.service';
import { getProductByType, getSelfServiceOrders } from 'app/shared/model/products.model';
import { flash } from 'light-it-up';
import * as _ from 'lodash';
import * as $ from 'jquery';
import { interval } from 'rxjs/index';
import { Subscription } from 'rxjs/Rx';
import { CheckoutDialogService } from 'app/shop/checkout-dialog.service';
import { SUCCESS_FLASH_DURATION } from 'app/shop/checkout.component';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';

const DEFAULT_COUNTDOWN_SECONDS = 300;
const DEFAULT_DIALOG_TIMEOUT_SECONDS = 10;
const DEFAULT_WEBSOCKET_RETRY_TIMEOUT = 5000;

@Component({
    selector: 'jhi-self-service',
    templateUrl: './self-service.component.html',
    styleUrls: ['self-service.component.scss']
})
export class SelfServiceComponent implements OnDestroy {
    qrCodeSize = 250;
    error: string;
    orders: Invoice[];
    memoPrefix: string;
    countdownSubscription: Subscription;
    countdownSeconds = DEFAULT_COUNTDOWN_SECONDS;

    constructor(
        private checkoutService: CheckoutService,
        private checkoutDialogService: CheckoutDialogService,
        private route: ActivatedRoute
    ) {
        this.route.paramMap.subscribe((params: ParamMap) => (this.memoPrefix = params.get('id')));
        this.startCountdown();
        this.setupInvoices();
        this.listenOnWebSocket();
    }

    listenOnWebSocket() {
        this.checkoutService.connect(() => {
            setTimeout(() => {
                this.listenOnWebSocket();
            }, DEFAULT_WEBSOCKET_RETRY_TIMEOUT);
        });
        this.checkoutService.subscribe();
        this.checkoutService.receive().subscribe(val => {
            const invoice = _.find(this.orders, ['referenceId', val.referenceId]);
            if (invoice && val.settled === true) {
                this.invoicePaid(invoice);
            }
        });
    }

    @HostListener('window:beforeunload', ['$event'])
    onPageUnload($event) {
        this.ngOnDestroy();
    }

    ngOnDestroy() {
        if (this.countdownSubscription) {
            this.countdownSubscription.unsubscribe();
        }
        this.checkoutService.unsubscribe();
        this.checkoutService.disconnect();
    }

    startCountdown() {
        this.countdownSubscription = interval(1000).subscribe(() => {
            this.countdownSeconds -= 1;
            if (this.countdownSeconds <= 0) {
                this.setupInvoices();
                this.countdownSeconds = DEFAULT_COUNTDOWN_SECONDS;
            }
        });
    }

    setupInvoices() {
        this.orders = getSelfServiceOrders();
        this.orders.forEach((order, index) => {
            order.memoPrefix = this.memoPrefix;
            this.checkoutService.createInvoice(order).subscribe((invoice: Invoice) => {
                this.orders[index] = invoice;
            }, err => (this.error = err.error.message));
        });
    }

    invoicePaid(invoice: Invoice) {
        const index = this.orders.indexOf(invoice);
        flash({
            element: $('#flash-receiver')[0],
            colorFlash: '#3b7bbe',
            zIndex: 9999,
            duration: SUCCESS_FLASH_DURATION
        });
        setTimeout(() => {
            this.checkoutDialogService.openDialog(invoice, true, DEFAULT_DIALOG_TIMEOUT_SECONDS);
            if (index >= 0) {
                this.orders[index] = getSelfServiceOrders()[index];
                this.checkoutService.createInvoice(this.orders[index]).subscribe((newInvoice: Invoice) => {
                    this.orders[index] = newInvoice;
                }, err => (this.error = err.error.message));
            }
        }, SUCCESS_FLASH_DURATION);
    }

    product(index) {
        return getProductByType(this.orders[index].orderItems[0].itemType);
    }
}
