/*
 * Copyright 2008-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.broadleafcommerce.core.offer.domain;

import org.broadleafcommerce.core.order.domain.FulfillmentGroup;
import org.broadleafcommerce.common.money.Money;

public interface FulfillmentGroupAdjustment extends Adjustment {

    public FulfillmentGroup getFulfillmentGroup();

    public void init(FulfillmentGroup fulfillmentGroup, Offer offer, String reason);

    public void setValue(Money value);

    public void setFulfillmentGroup(FulfillmentGroup fulfillmentGroup);
    
}
