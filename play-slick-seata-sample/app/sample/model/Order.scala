package sample.model

import sample.model.OrderStatus.OrderStatus


case class Order(id: Long, userId: Long, productId: Long, status: OrderStatus, payAmount: Int)
