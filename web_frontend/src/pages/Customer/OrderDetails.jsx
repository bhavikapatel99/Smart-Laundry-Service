
import React, { useEffect, useState, useRef } from "react";
import { useParams } from "react-router-dom";
import axios from "../../utils/axiosInstance";
import OrderBill from "./OrderBill";
import html2pdf from "html2pdf.js";
import RazorpayButton from "../../components/RazorpayButton"; 

export default function OrderDetails() {
  const { orderId } = useParams();
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const invoiceRef = useRef();

  useEffect(() => {
    const fetchSummary = async () => {
      try {
        const res = await axios.get(`/orders/${orderId}/bill`);
        setSummary(res.data);
      } catch (err) {
        console.error(err);
        setError("Failed to load order summary.");
      } finally {
        setLoading(false);
      }
    };
    fetchSummary();
  }, [orderId]);

  const downloadPDF = () => {
    const element = invoiceRef.current;
    const opt = {
      margin: 0.5,
      filename: `Laundry_Bill_${orderId}.pdf`,
      image: { type: "jpeg", quality: 0.98 },
      html2canvas: { scale: 2 },
      jsPDF: { unit: "in", format: "letter", orientation: "portrait" },
    };
    html2pdf().set(opt).from(element).save();
  };

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <h2 className="text-2xl font-bold mb-4 text-blue-700">Order Details</h2>

      {loading && <p>Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && summary && (
        <>
          <OrderBill summary={summary} refProp={invoiceRef} />

          {/* ✅ Razorpay Payment Button when not paid and order delivered */}
          {summary.status !== "PAID" &&
            summary.invoiceNumber &&
            summary.finalAmount > 0 &&
            summary.orderStatus === "DELIVERED" && (
              <div className="text-center mt-4">
                <RazorpayButton
                  billId={summary.invoiceNumber}
                  finalPrice={summary.finalAmount}
                  orderId={orderId}
                />
              </div>
            )}

          {summary.status === "PAID" && (
            <p className="text-center mt-4 text-green-700 font-semibold">
              ✅ Payment already completed.
            </p>
          )}

         
        </>
      )}
    </div>
  );
}
